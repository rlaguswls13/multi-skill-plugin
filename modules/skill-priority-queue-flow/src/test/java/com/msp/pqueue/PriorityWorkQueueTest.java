package com.msp.pqueue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PriorityWorkQueue & WorkerPool Unit Tests")
class PriorityWorkQueueTest {

    @Test
    @DisplayName("Strict priority drain processes higher priority items first")
    void testStrictPriorityDrain() {
        PriorityWorkQueue<String> queue = new PriorityWorkQueue<>();
        queue.setFairSchedulingEnabled(false); // Strict mode

        queue.submit("Low-1", Priority.LOW);
        queue.submit("Normal-1", Priority.NORMAL);
        queue.submit("High-1", Priority.HIGH);
        queue.submit("Critical-1", Priority.CRITICAL);
        queue.submit("Critical-2", Priority.CRITICAL);

        assertEquals(5, queue.size());
        assertEquals(2, queue.size(Priority.CRITICAL));
        assertEquals(1, queue.size(Priority.HIGH));

        assertEquals("Critical-1", queue.poll());
        assertEquals("Critical-2", queue.poll());
        assertEquals("High-1", queue.poll());
        assertEquals("Normal-1", queue.poll());
        assertEquals("Low-1", queue.poll());
        assertNull(queue.poll());
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("Fair scheduling prevents starvation of lower-priority items")
    void testFairSchedulingAntiStarvation() {
        // Configure weights: CRITICAL gets 3, LOW gets 1
        PriorityWorkQueue<String> queue = new PriorityWorkQueue<>(3, 2, 2, 1);

        // Submit 6 Critical tasks and 1 Low task
        for (int i = 1; i <= 6; i++) {
            queue.submit("Crit-" + i, Priority.CRITICAL);
        }
        queue.submit("Low-Task", Priority.LOW);

        List<String> drained = new ArrayList<>();
        while (!queue.isEmpty()) {
            drained.add(queue.poll());
        }

        // Expected order:
        // Round 1: Critical gets 3 items ("Crit-1", "Crit-2", "Crit-3").
        // Since Critical exhausted its quota (3), the scheduler checks the next available queue with quota: LOW (quota 1).
        // Item 4 should be "Low-Task"!
        // Then LOW is empty, quotas replenish, and remaining Critical items ("Crit-4", "Crit-5", "Crit-6") are drained.
        assertEquals(7, drained.size());
        assertEquals("Crit-1", drained.get(0));
        assertEquals("Crit-2", drained.get(1));
        assertEquals("Crit-3", drained.get(2));
        assertEquals("Low-Task", drained.get(3), "Low priority task must be serviced without total starvation");
        assertEquals("Crit-4", drained.get(4));
        assertEquals("Crit-5", drained.get(5));
        assertEquals("Crit-6", drained.get(6));
    }

    @Test
    @DisplayName("Blocking take and timeout poll")
    void testBlockingTakeAndPollTimeout() throws InterruptedException {
        PriorityWorkQueue<String> queue = new PriorityWorkQueue<>();

        // Poll with short timeout should return null
        long start = System.currentTimeMillis();
        String result = queue.poll(Duration.ofMillis(50));
        long elapsed = System.currentTimeMillis() - start;
        assertNull(result);
        assertTrue(elapsed >= 40);

        // Blocking take with producer
        CountDownLatch producerStarted = new CountDownLatch(1);
        Thread.ofVirtual().start(() -> {
            producerStarted.countDown();
            try {
                Thread.sleep(60);
                queue.submit("Async-Item", Priority.HIGH);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producerStarted.await();
        String item = queue.take();
        assertEquals("Async-Item", item);
    }

    @Test
    @DisplayName("WorkerPool async processing with Virtual Threads and graceful shutdown")
    void testWorkerPoolProcessingAndShutdown() throws Exception {
        PriorityWorkQueue<Integer> queue = new PriorityWorkQueue<>();
        List<Integer> processed = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(30);

        WorkerPool<Integer> pool = WorkerPool.builder(queue, (Integer num) -> {
                    processed.add(num);
                    latch.countDown();
                })
                .workers(4)
                .useVirtualThreads(true)
                .build();

        pool.start();
        assertTrue(pool.isRunning());

        // Submit 30 items
        for (int i = 0; i < 30; i++) {
            Priority p = (i % 4 == 0) ? Priority.CRITICAL : Priority.NORMAL;
            queue.submit(i, p);
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "All items should be processed by workers");
        assertEquals(30, processed.size());
        assertEquals(30, pool.getProcessedCount());
        assertEquals(0, pool.getFailureCount());

        pool.stop();
        boolean terminated = pool.awaitTermination(Duration.ofSeconds(2));
        assertTrue(terminated);
        assertFalse(pool.isRunning());
    }

    @Test
    @DisplayName("WorkerPool pause and resume functionality")
    void testWorkerPoolPauseResume() throws Exception {
        PriorityWorkQueue<String> queue = new PriorityWorkQueue<>();
        List<String> processed = Collections.synchronizedList(new ArrayList<>());

        try (WorkerPool<String> pool = WorkerPool.builder(queue, processed::add)
                .workers(2)
                .build()) {

            pool.start();
            pool.pause();
            assertTrue(pool.isPaused());

            queue.submit("Paused-Item");
            Thread.sleep(150);
            assertEquals(0, processed.size(), "No items should be processed while paused");

            pool.resume();
            assertFalse(pool.isPaused());
            Thread.sleep(150);
            assertEquals(1, processed.size());
            assertEquals("Paused-Item", processed.get(0));
        }
    }

    @Test
    @DisplayName("WorkerPool error handling callback")
    void testWorkerPoolErrorHandling() throws Exception {
        PriorityWorkQueue<String> queue = new PriorityWorkQueue<>();
        AtomicBoolean errorCaught = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        try (WorkerPool<String> pool = WorkerPool.builder(queue, (String item) -> {
                    throw new RuntimeException("Simulated worker error on " + item);
                })
                .onError((item, ex) -> {
                    if ("Failing-Item".equals(item)) {
                        errorCaught.set(true);
                    }
                    latch.countDown();
                })
                .workers(1)
                .build()) {

            pool.start();
            queue.submit("Failing-Item");
            boolean signaled = latch.await(3, TimeUnit.SECONDS);

            assertTrue(signaled);
            assertTrue(errorCaught.get());
            assertEquals(1, pool.getFailureCount());
        }
    }
}
