package com.msp.priorexec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PriorityTaskExecutor Tests")
class PriorityTaskExecutorTest {

    @Test
    @DisplayName("Tasks execute in strict priority order (higher priority first)")
    void testPriorityOrdering() throws Exception {
        try (PriorityTaskExecutor executor = PriorityTaskExecutor.newSingleThreadExecutor()) {
            CountDownLatch blockerLatch = new CountDownLatch(1);
            CountDownLatch taskStarted = new CountDownLatch(1);
            List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());

            // 1. Submit blocker to occupy the single thread
            executor.run(() -> {
                taskStarted.countDown();
                try {
                    blockerLatch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, 10);

            assertTrue(taskStarted.await(1, TimeUnit.SECONDS));

            // 2. Submit tasks with different priorities while worker is blocked
            CompletableFuture<Void> fLow = executor.run(() -> executionOrder.add(2), 2);
            CompletableFuture<Void> fHigh = executor.run(() -> executionOrder.add(9), 9);
            CompletableFuture<Void> fMed = executor.run(() -> executionOrder.add(5), 5);

            assertEquals(3, executor.getQueueDepth());

            // 3. Release blocker
            blockerLatch.countDown();

            CompletableFuture.allOf(fLow, fHigh, fMed).get(3, TimeUnit.SECONDS);

            assertEquals(List.of(9, 5, 2), executionOrder);
            assertEquals(4, executor.getCompletedTaskCount());
            assertEquals(0, executor.getExpiredTaskCount());
        }
    }

    @Test
    @DisplayName("Tasks with equal priority preserve FIFO submission order")
    void testFifoStabilityForEqualPriority() throws Exception {
        try (PriorityTaskExecutor executor = PriorityTaskExecutor.newSingleThreadExecutor()) {
            CountDownLatch blockerLatch = new CountDownLatch(1);
            CountDownLatch taskStarted = new CountDownLatch(1);
            List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

            executor.run(() -> {
                taskStarted.countDown();
                try {
                    blockerLatch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, 10);

            assertTrue(taskStarted.await(1, TimeUnit.SECONDS));

            CompletableFuture<Void> f1 = executor.run(() -> executionOrder.add("Task-1"), 5);
            CompletableFuture<Void> f2 = executor.run(() -> executionOrder.add("Task-2"), 5);
            CompletableFuture<Void> f3 = executor.run(() -> executionOrder.add("Task-3"), 5);

            blockerLatch.countDown();

            CompletableFuture.allOf(f1, f2, f3).get(3, TimeUnit.SECONDS);

            assertEquals(List.of("Task-1", "Task-2", "Task-3"), executionOrder);
        }
    }

    @Test
    @DisplayName("Queued task that exceeds TTL completes exceptionally with TaskExpiredException")
    void testTaskTtlExpiration() throws Exception {
        try (PriorityTaskExecutor executor = PriorityTaskExecutor.newSingleThreadExecutor()) {
            CountDownLatch blockerLatch = new CountDownLatch(1);
            CountDownLatch taskStarted = new CountDownLatch(1);

            executor.run(() -> {
                taskStarted.countDown();
                try {
                    blockerLatch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, 10);

            assertTrue(taskStarted.await(1, TimeUnit.SECONDS));

            // Task with short 30ms TTL
            CompletableFuture<String> expiredFuture = executor.submit(
                    () -> "should-not-run",
                    5,
                    Duration.ofMillis(30)
            );

            // Sleep longer than TTL while worker is blocked
            Thread.sleep(70);

            blockerLatch.countDown();

            ExecutionException ex = assertThrows(ExecutionException.class, () ->
                    expiredFuture.get(2, TimeUnit.SECONDS));

            assertInstanceOf(TaskExpiredException.class, ex.getCause());
            TaskExpiredException tee = (TaskExpiredException) ex.getCause();
            assertEquals(5, tee.getPriority());
            assertTrue(tee.getWaitedDuration().toMillis() >= 30);
            assertEquals(1, executor.getExpiredTaskCount());
        }
    }

    @Test
    @DisplayName("Exception thrown by task is properly propagated through CompletableFuture")
    void testTaskExceptionPropagation() {
        try (PriorityTaskExecutor executor = PriorityTaskExecutor.newSingleThreadExecutor()) {
            CompletableFuture<String> future = executor.submit(() -> {
                throw new IllegalStateException("Simulated business error");
            });

            ExecutionException ex = assertThrows(ExecutionException.class, () ->
                    future.get(2, TimeUnit.SECONDS));

            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertEquals("Simulated business error", ex.getCause().getMessage());
        }
    }

    @Test
    @DisplayName("Graceful shutdown waits for tasks to finish")
    void testGracefulShutdown() throws Exception {
        PriorityTaskExecutor executor = PriorityTaskExecutor.newFixedThreadPool(2);
        CompletableFuture<Integer> f1 = executor.submit(() -> {
            Thread.sleep(50);
            return 42;
        });

        executor.shutdown();
        assertTrue(executor.isShutdown());
        assertTrue(executor.awaitTermination(Duration.ofSeconds(2)));
        assertTrue(executor.isTerminated());
        assertEquals(42, f1.get());
    }
}
