package com.msp.vthread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VirtualThreadRunner Unit Tests")
class VirtualThreadRunnerTest {

    @Test
    @DisplayName("Run 1,000 tasks and verify concurrency never exceeds semaphore limit")
    void testConcurrencyRateLimiting1000Tasks() throws Exception {
        int maxPermits = 10;
        int totalTasks = 1_000;

        AtomicInteger activeTasks = new AtomicInteger(0);
        AtomicInteger maxObservedConcurrency = new AtomicInteger(0);
        AtomicBoolean allVirtual = new AtomicBoolean(true);

        try (VirtualThreadRunner runner = VirtualThreadRunner.withMaxConcurrency(maxPermits)) {
            assertEquals(maxPermits, runner.getMaxConcurrency());

            List<CompletableFuture<Integer>> futures = new ArrayList<>(totalTasks);

            for (int i = 0; i < totalTasks; i++) {
                final int taskId = i;
                futures.add(runner.submit(() -> {
                    if (!Thread.currentThread().isVirtual()) {
                        allVirtual.set(false);
                    }

                    int current = activeTasks.incrementAndGet();
                    maxObservedConcurrency.accumulateAndGet(current, Math::max);

                    // Simulate lightweight async work
                    Thread.sleep(2);

                    activeTasks.decrementAndGet();
                    return taskId * 2;
                }));
            }

            // Wait for all 1,000 tasks to complete
            runner.awaitAll();

            assertTrue(allVirtual.get(), "Tasks must execute on Virtual Threads");
            assertEquals(totalTasks, runner.getCompletedCount());
            assertEquals(0, runner.getActiveCount());
            assertEquals(0, runner.getQueuedCount());

            // Key verification: active concurrency must NEVER exceed maxPermits!
            assertTrue(maxObservedConcurrency.get() <= maxPermits,
                    "Observed concurrency (" + maxObservedConcurrency.get() + ") must not exceed permit limit (" + maxPermits + ")");
            assertTrue(maxObservedConcurrency.get() > 1, "Concurrency should be greater than 1 under parallel load");

            // Verify all future results
            for (int i = 0; i < totalTasks; i++) {
                assertEquals(i * 2, futures.get(i).join());
            }
        }
    }

    @Test
    @DisplayName("Exception handling completes future exceptionally and releases semaphore permit")
    void testExceptionHandlingAndPermitRelease() throws Exception {
        try (VirtualThreadRunner runner = VirtualThreadRunner.withMaxConcurrency(2)) {
            CompletableFuture<String> failingFuture = runner.submit(() -> {
                throw new IllegalStateException("Simulated downstream error");
            });

            ExecutionException ex = assertThrows(ExecutionException.class, failingFuture::get);
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertEquals("Simulated downstream error", ex.getCause().getMessage());

            // Verify permits are not leaked: submit normal tasks next
            CompletableFuture<String> successFuture = runner.submit(() -> "Success after error");
            assertEquals("Success after error", successFuture.get());
            assertEquals(2, runner.getCompletedCount());
        }
    }

    @Test
    @DisplayName("Unbounded virtual thread runner execution")
    void testUnboundedExecution() throws Exception {
        try (VirtualThreadRunner runner = VirtualThreadRunner.unbounded()) {
            assertEquals(Integer.MAX_VALUE, runner.getMaxConcurrency());

            List<CompletableFuture<Void>> futures = runner.runAll(List.of(
                    () -> {},
                    () -> {},
                    () -> {}
            ));

            runner.awaitAll();
            assertEquals(3, runner.getCompletedCount());
            for (var f : futures) {
                assertTrue(f.isDone());
                assertFalse(f.isCompletedExceptionally());
            }
        }
    }

    @Test
    @DisplayName("awaitAll with timeout bounds wait duration")
    void testAwaitAllTimeout() throws Exception {
        try (VirtualThreadRunner runner = VirtualThreadRunner.withMaxConcurrency(4)) {
            runner.submit(() -> {
                Thread.sleep(1500);
                return "Slow";
            });

            // Awaiting with short timeout of 50ms should return false
            boolean finished = runner.awaitAll(Duration.ofMillis(50));
            assertFalse(finished);

            // Awaiting with sufficient timeout should return true
            boolean finishedEventual = runner.awaitAll(Duration.ofSeconds(3));
            assertTrue(finishedEventual);
        }
    }

    @Test
    @DisplayName("Submitting task after runner close throws RejectedExecutionException")
    void testRejectionAfterClose() {
        VirtualThreadRunner runner = VirtualThreadRunner.withMaxConcurrency(2);
        runner.close();
        assertTrue(runner.isClosed());

        assertThrows(RejectedExecutionException.class, () -> runner.run(() -> {}));
        assertThrows(RejectedExecutionException.class, () -> runner.submit(() -> "test"));
    }
}
