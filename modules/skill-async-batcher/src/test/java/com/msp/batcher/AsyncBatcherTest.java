package com.msp.batcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AsyncBatcherTest {

    @Test
    @DisplayName("Batching by size: should dispatch immediate batches when maxBatchSize is reached")
    void batchBySizeTrigger() throws InterruptedException {
        List<List<Integer>> receivedBatches = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        try (var batcher = AsyncBatcher.<Integer>builder()
                .maxBatchSize(5)
                .maxWaitDuration(Duration.ofSeconds(10)) // Long timeout so size triggers it
                .consumer(batch -> {
                    receivedBatches.add(new ArrayList<>(batch));
                    latch.countDown();
                })
                .build()) {

            for (int i = 1; i <= 10; i++) {
                batcher.submit(i);
            }

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Two batches should have triggered by size");
            assertEquals(2, receivedBatches.size());
            assertEquals(List.of(1, 2, 3, 4, 5), receivedBatches.get(0));
            assertEquals(List.of(6, 7, 8, 9, 10), receivedBatches.get(1));
            assertEquals(10, batcher.totalItemsProcessed());
            assertEquals(2, batcher.totalBatchesProcessed());
        }
    }

    @Test
    @DisplayName("Batching by timeout: should dispatch partial batch when maxWaitDuration expires")
    void batchByTimeoutTrigger() throws InterruptedException {
        List<List<String>> receivedBatches = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (var batcher = AsyncBatcher.<String>builder()
                .maxBatchSize(20)
                .maxWaitDuration(Duration.ofMillis(60))
                .consumer(batch -> {
                    receivedBatches.add(new ArrayList<>(batch));
                    latch.countDown();
                })
                .build()) {

            batcher.submit("alpha");
            batcher.submit("beta");
            batcher.submit("gamma");

            assertTrue(latch.await(1, TimeUnit.SECONDS), "Timeout should have triggered batch dispatch");
            assertEquals(1, receivedBatches.size());
            assertEquals(List.of("alpha", "beta", "gamma"), receivedBatches.get(0));
        }
    }

    @Test
    @DisplayName("Concurrent submissions: 20 threads submitting 50 items each should process exactly 1000 items")
    void concurrentSubmissions() throws InterruptedException {
        List<Integer> allProcessed = new CopyOnWriteArrayList<>();
        int threadCount = 20;
        int itemsPerThread = 50;
        int expectedTotal = threadCount * itemsPerThread;

        try (var batcher = AsyncBatcher.<Integer>builder()
                .maxBatchSize(25)
                .maxWaitDuration(Duration.ofMillis(40))
                .consumer(allProcessed::addAll)
                .build()) {

            ExecutorService threadPool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                threadPool.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < itemsPerThread; i++) {
                            batcher.submit(threadId * 1000 + i);
                        }
                    } catch (InterruptedException ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            threadPool.shutdown();

            batcher.flush();

            assertEquals(expectedTotal, allProcessed.size());
            assertEquals(expectedTotal, batcher.totalSubmittedCount());
            assertEquals(expectedTotal, batcher.totalItemsProcessed());
        }
    }

    @Test
    @DisplayName("Complete flush on close: all pending items must be delivered before close returns")
    void flushOnClose() {
        List<Integer> consumed = new CopyOnWriteArrayList<>();
        AsyncBatcher<Integer> batcher = AsyncBatcher.<Integer>builder()
                .maxBatchSize(100)
                .maxWaitDuration(Duration.ofSeconds(60))
                .consumer(consumed::addAll)
                .build();

        batcher.submit(1);
        batcher.submit(2);
        batcher.submit(3);

        assertEquals(3, batcher.pendingCount());
        batcher.close();

        assertEquals(0, batcher.pendingCount());
        assertEquals(List.of(1, 2, 3), consumed);
        assertTrue(batcher.isClosed());
    }

    @Test
    @DisplayName("Manual flush: synchronously flushes buffer before timeout")
    void manualFlush() {
        List<String> items = new CopyOnWriteArrayList<>();
        try (var batcher = AsyncBatcher.<String>builder()
                .maxBatchSize(100)
                .maxWaitDuration(Duration.ofSeconds(60))
                .consumer(items::addAll)
                .build()) {

            batcher.submitAll(List.of("A", "B", "C"));
            assertEquals(3, batcher.pendingCount());

            batcher.flush();

            assertEquals(0, batcher.pendingCount());
            assertEquals(List.of("A", "B", "C"), items);
        }
    }

    @Test
    @DisplayName("Error handler invoked when consumer throws exception")
    void errorHandlerInvocation() {
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Integer> failedItems = new CopyOnWriteArrayList<>();

        try (var batcher = AsyncBatcher.<Integer>builder()
                .maxBatchSize(2)
                .maxWaitDuration(Duration.ofSeconds(10))
                .consumer(batch -> {
                    throw new RuntimeException("Simulated consumer crash");
                })
                .errorHandler((batch, ex) -> {
                    errorCount.incrementAndGet();
                    failedItems.addAll(batch);
                })
                .build()) {

            batcher.submit(10);
            batcher.submit(20);
            batcher.flush();

            assertEquals(1, errorCount.get());
            assertEquals(List.of(10, 20), failedItems);
        }
    }
}
