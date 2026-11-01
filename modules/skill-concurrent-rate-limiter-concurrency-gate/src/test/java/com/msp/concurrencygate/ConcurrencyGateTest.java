package com.msp.concurrencygate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyGateTest {

    @Test
    @DisplayName("Enforces maxConcurrency with direct tryAcquire and release")
    void testDirectAcquireRelease() {
        try (ConcurrencyGate gate = ConcurrencyGate.of(2, 5)) {
            assertTrue(gate.tryAcquire());
            assertEquals(1, gate.getActiveCount());

            assertTrue(gate.tryAcquire());
            assertEquals(2, gate.getActiveCount());

            // Exceeds max concurrency of 2
            assertFalse(gate.tryAcquire());

            gate.release();
            assertEquals(1, gate.getActiveCount());

            assertTrue(gate.tryAcquire());
            assertEquals(2, gate.getActiveCount());
        }
    }

    @Test
    @DisplayName("Rejects tasks immediately when queue capacity is exceeded")
    void testQueueCapacityExceeded() {
        try (ConcurrencyGate gate = ConcurrencyGate.of(1, 2)) {
            // Task 1: active
            CompletableFuture<String> task1Signal = new CompletableFuture<>();
            CompletableFuture<String> future1 = gate.supplyAsync(() -> task1Signal);

            assertEquals(1, gate.getActiveCount());
            assertEquals(0, gate.getQueueDepth());

            // Task 2: queued (slot 1 of 2)
            CompletableFuture<String> future2 = gate.supplyAsync(() -> CompletableFuture.completedFuture("two"));
            assertEquals(1, gate.getQueueDepth());

            // Task 3: queued (slot 2 of 2)
            CompletableFuture<String> future3 = gate.supplyAsync(() -> CompletableFuture.completedFuture("three"));
            assertEquals(2, gate.getQueueDepth());

            // Task 4: rejected!
            CompletableFuture<String> future4 = gate.supplyAsync(() -> CompletableFuture.completedFuture("four"));

            ExecutionException ex = assertThrows(ExecutionException.class, future4::get);
            assertInstanceOf(GateCapacityExceededException.class, ex.getCause());
            assertEquals(1, gate.getTotalRejected());

            // Finish task 1
            task1Signal.complete("one");
            assertEquals("one", assertDoesNotThrow(() -> future1.get(1, TimeUnit.SECONDS)));
            assertEquals("two", assertDoesNotThrow(() -> future2.get(1, TimeUnit.SECONDS)));
            assertEquals("three", assertDoesNotThrow(() -> future3.get(1, TimeUnit.SECONDS)));
        }
    }

    @Test
    @DisplayName("Fails queued task with GateTimeoutException when queue wait exceeds timeout")
    void testQueueTimeout() {
        try (ConcurrencyGate gate = ConcurrencyGate.of(1, 10)) {
            CompletableFuture<Void> blocker = new CompletableFuture<>();
            CompletableFuture<String> first = gate.supplyAsync(() -> blocker.thenApply(v -> "first"));

            CompletableFuture<String> queued = gate.supplyAsync(
                    () -> CompletableFuture.completedFuture("second"),
                    Duration.ofMillis(50)
            );

            ExecutionException ex = assertThrows(ExecutionException.class, () -> queued.get(1, TimeUnit.SECONDS));
            assertInstanceOf(GateTimeoutException.class, ex.getCause());

            blocker.complete(null);
            assertEquals("first", assertDoesNotThrow(() -> first.get(1, TimeUnit.SECONDS)));
        }
    }

    @Test
    @DisplayName("Executes multiple asynchronous tasks without exceeding max concurrency")
    void testConcurrentExecutionLimit() throws Exception {
        int maxConcurrency = 3;
        int taskCount = 15;

        try (ConcurrencyGate gate = ConcurrencyGate.of(maxConcurrency, 50)) {
            AtomicInteger activePeak = new AtomicInteger(0);
            List<CompletableFuture<Integer>> futures = new ArrayList<>();

            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                CompletableFuture<Integer> f = gate.supplyAsync(() -> {
                    int current = gate.getActiveCount();
                    activePeak.accumulateAndGet(current, Math::max);

                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return taskId;
                    });
                });
                futures.add(f);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);

            assertTrue(activePeak.get() <= maxConcurrency, "Peak concurrency " + activePeak.get() + " exceeded " + maxConcurrency);
            assertEquals(0, gate.getActiveCount());
            assertEquals(taskCount, gate.getTotalAcquired());
        }
    }

    @Test
    @DisplayName("Supports synchronous submit and runAsync wrappers")
    void testSubmitAndRunAsync() throws Exception {
        try (ConcurrencyGate gate = ConcurrencyGate.of(2, 5)) {
            CompletableFuture<String> submitFuture = gate.submit(() -> "hello from submit");
            assertEquals("hello from submit", submitFuture.get(1, TimeUnit.SECONDS));

            AtomicInteger flag = new AtomicInteger(0);
            CompletableFuture<Void> runFuture = gate.runAsync(() -> flag.set(42));
            runFuture.get(1, TimeUnit.SECONDS);
            assertEquals(42, flag.get());
        }
    }

    @Test
    @DisplayName("Synchronous tryAcquire with timeout succeeds when slot freed")
    void testTryAcquireWithTimeout() throws Exception {
        try (ConcurrencyGate gate = ConcurrencyGate.of(1, 5)) {
            assertTrue(gate.tryAcquire());

            // Release after 50ms from another thread
            Executors.newSingleThreadScheduledExecutor().schedule(gate::release, 50, TimeUnit.MILLISECONDS);

            assertTrue(gate.tryAcquire(Duration.ofMillis(300)));
            gate.release();
        }
    }

    @Test
    @DisplayName("Reports accurate GateMetrics snapshot")
    void testGateMetrics() {
        try (ConcurrencyGate gate = ConcurrencyGate.of(2, 10)) {
            gate.tryAcquire();
            GateMetrics metrics = gate.getMetrics();

            assertEquals(1, metrics.activeCount());
            assertEquals(0, metrics.queueDepth());
            assertEquals(1, metrics.totalAcquired());
            assertEquals(0, metrics.totalRejected());
            assertEquals(2, metrics.maxConcurrency());
            assertEquals(10, metrics.maxQueueCapacity());
        }
    }
}
