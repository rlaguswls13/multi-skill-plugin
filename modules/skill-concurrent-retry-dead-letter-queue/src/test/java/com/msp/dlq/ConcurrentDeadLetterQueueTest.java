package com.msp.dlq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentDeadLetterQueueTest {

    @Test
    @DisplayName("Enqueue poison messages and query metrics")
    void testEnqueueAndMetrics() {
        DeadLetterQueue<String> queue = ConcurrentDeadLetterQueue.inMemory();

        queue.enqueue("order-101", new IllegalArgumentException("Invalid price"), 3, Map.of("source", "checkout"));
        queue.enqueue("order-102", new NullPointerException("Null customer"), 3);

        assertEquals(2, queue.size());
        DlqMetrics metrics = queue.metrics();
        assertEquals(2, metrics.totalQuarantined());
        assertEquals(2, metrics.activeQuarantined());
        assertEquals(0, metrics.replaySuccessCount());
        assertEquals(0, metrics.replayFailureCount());
        assertEquals(0, metrics.discardedCount());

        List<DeadLetterMessage<String>> all = queue.listAll();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("Search by error type, payload keyword, and time range")
    void testSearchAndFilters() {
        DeadLetterQueue<String> queue = ConcurrentDeadLetterQueue.inMemory();

        queue.enqueue("item-apple", new IllegalStateException("Out of stock"), 1);
        queue.enqueue("item-banana", new IllegalArgumentException("Invalid weight"), 2);
        queue.enqueue("user-charlie", new RuntimeException("Auth timeout"), 3);

        List<DeadLetterMessage<String>> illegalArgs = queue.findByErrorType("IllegalArgumentException");
        assertEquals(1, illegalArgs.size());
        assertEquals("item-banana", illegalArgs.getFirst().payload());

        List<DeadLetterMessage<String>> itemMatches = queue.findByPayloadKeyword("item-");
        assertEquals(2, itemMatches.size());

        Instant now = Instant.now();
        List<DeadLetterMessage<String>> inRange = queue.findByTimeRange(now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        assertEquals(3, inRange.size());
    }

    @Test
    @DisplayName("Replay successfully reprocesses and updates status")
    void testReplaySuccess() {
        DeadLetterQueue<String> queue = ConcurrentDeadLetterQueue.inMemory();

        queue.enqueue("payload-1", new RuntimeException("DB down"), 2);
        queue.enqueue("payload-2", new RuntimeException("DB down"), 2);

        AtomicInteger replayedCount = new AtomicInteger();
        long success = queue.replay(msg -> replayedCount.incrementAndGet());

        assertEquals(2, success);
        assertEquals(2, replayedCount.get());
        assertEquals(0, queue.size()); // No active quarantined

        DlqMetrics metrics = queue.metrics();
        assertEquals(2, metrics.totalQuarantined());
        assertEquals(0, metrics.activeQuarantined());
        assertEquals(2, metrics.replaySuccessCount());
        assertEquals(0, metrics.replayFailureCount());
    }

    @Test
    @DisplayName("Replay failure increments attempts and leaves in quarantine")
    void testReplayFailure() {
        DeadLetterQueue<String> queue = ConcurrentDeadLetterQueue.inMemory();

        queue.enqueue("bad-payload", new RuntimeException("Network error"), 2);
        DeadLetterMessage<String> initial = queue.listQuarantined().getFirst();

        boolean replayed = queue.replayOne(initial.id(), msg -> {
            throw new RuntimeException("Still unreachable");
        });

        assertFalse(replayed);
        assertEquals(1, queue.size());

        DeadLetterMessage<String> updated = queue.findById(initial.id()).orElseThrow();
        assertEquals(MessageStatus.QUARANTINED, updated.status());
        assertEquals(3, updated.attempts()); // 2 + 1

        DlqMetrics metrics = queue.metrics();
        assertEquals(1, metrics.replayFailureCount());
    }

    @Test
    @DisplayName("Discard message marks status as DISCARDED")
    void testDiscard() {
        DeadLetterQueue<String> queue = ConcurrentDeadLetterQueue.inMemory();

        queue.enqueue("msg-to-discard", new RuntimeException("Corrupted"), 5);
        DeadLetterMessage<String> msg = queue.listQuarantined().getFirst();

        assertTrue(queue.discard(msg.id()));
        assertEquals(0, queue.size());

        DeadLetterMessage<String> discarded = queue.findById(msg.id()).orElseThrow();
        assertEquals(MessageStatus.DISCARDED, discarded.status());
        assertEquals(1, queue.metrics().discardedCount());
    }

    @Test
    @DisplayName("Purge clears all messages from queue")
    void testPurge() {
        DeadLetterQueue<String> queue = ConcurrentDeadLetterQueue.inMemory();

        queue.enqueue("m1", new RuntimeException(), 1);
        queue.enqueue("m2", new RuntimeException(), 1);

        assertEquals(2, queue.purge());
        assertEquals(0, queue.size());
        assertTrue(queue.listAll().isEmpty());
    }

    @Test
    @DisplayName("File persistence store saves, updates, and reloads across instances")
    void testFilePersistence(@TempDir Path tempDir) {
        Path filePath = tempDir.resolve("dlq-store.dat");

        ConcurrentDeadLetterQueue<String> queue1 = ConcurrentDeadLetterQueue.filePersistent(filePath);
        queue1.enqueue("persisted-payload-1", new IllegalArgumentException("Bad input"), 2, Map.of("env", "prod"));
        queue1.enqueue("persisted-payload-2", new RuntimeException("Disk full"), 1);

        assertEquals(2, queue1.size());

        // Reload in new instance
        ConcurrentDeadLetterQueue<String> queue2 = ConcurrentDeadLetterQueue.filePersistent(filePath);
        assertEquals(2, queue2.size());
        List<DeadLetterMessage<String>> reloaded = queue2.listQuarantined();
        assertEquals(2, reloaded.size());

        String targetId = reloaded.getFirst().id();
        boolean replayed = queue2.replayOne(targetId, msg -> {});
        assertTrue(replayed);

        // Verify third instance reflects the updated status
        ConcurrentDeadLetterQueue<String> queue3 = ConcurrentDeadLetterQueue.filePersistent(filePath);
        assertEquals(1, queue3.size());
        assertEquals(MessageStatus.REPLAYED, queue3.findById(targetId).orElseThrow().status());
    }

    @Test
    @DisplayName("Multi-threaded concurrent enqueues are safe and atomic")
    void testConcurrentEnqueues() throws InterruptedException {
        DeadLetterQueue<Integer> queue = ConcurrentDeadLetterQueue.inMemory();
        int threadCount = 8;
        int messagesPerThread = 50;

        try (ExecutorService pool = Executors.newFixedThreadPool(threadCount)) {
            CountDownLatch latch = new CountDownLatch(threadCount);
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                pool.submit(() -> {
                    try {
                        for (int j = 0; j < messagesPerThread; j++) {
                            queue.enqueue(threadId * 1000 + j, new RuntimeException("err"), 1);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }

        assertEquals(threadCount * messagesPerThread, queue.size());
        assertEquals(threadCount * messagesPerThread, queue.metrics().totalQuarantined());
    }
}
