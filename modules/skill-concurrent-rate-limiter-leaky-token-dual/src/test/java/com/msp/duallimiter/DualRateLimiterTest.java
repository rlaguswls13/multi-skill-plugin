package com.msp.duallimiter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DualRateLimiter Tests")
class DualRateLimiterTest {

    @Nested
    @DisplayName("Synchronous tryAcquire()")
    class SynchronousAcquisitionTests {

        @Test
        @DisplayName("Allows requests up to burst token capacity")
        void acquiresUpToCapacity() {
            try (DualRateLimiter limiter = DualRateLimiter.builder()
                    .tokenCapacity(5)
                    .tokenRefillRate(1)
                    .queueCapacity(10)
                    .leakRate(5)
                    .build()) {

                for (int i = 0; i < 5; i++) {
                    assertTrue(limiter.tryAcquire(), "Permit " + i + " should succeed");
                }
                assertFalse(limiter.tryAcquire(), "Permit 6 should be rejected due to token exhaustion");
                assertEquals(5, limiter.totalAccepted());
                assertEquals(1, limiter.totalRejected());
            }
        }

        @Test
        @DisplayName("Recharges tokens over time")
        void rechargesTokens() throws InterruptedException {
            try (DualRateLimiter limiter = DualRateLimiter.builder()
                    .tokenCapacity(2)
                    .tokenRefillRate(20) // 20 tokens/sec -> 1 token every 50ms
                    .queueCapacity(10)
                    .leakRate(20)
                    .build()) {

                assertTrue(limiter.tryAcquire());
                assertTrue(limiter.tryAcquire());
                assertFalse(limiter.tryAcquire());

                Thread.sleep(120); // allow refill of ~2 tokens
                assertTrue(limiter.tryAcquire());
            }
        }
    }

    @Nested
    @DisplayName("Asynchronous executeScheduled()")
    class ScheduledExecutionTests {

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        @DisplayName("Executes tasks and delivers uniform smooth draining")
        void executesTasksWithUniformDraining() throws Exception {
            // Drain at 20 items/sec (50ms interval)
            try (DualRateLimiter limiter = DualRateLimiter.builder()
                    .tokenCapacity(10)
                    .tokenRefillRate(10)
                    .leakRate(20)
                    .queueCapacity(10)
                    .build()) {

                List<Long> completionTimes = Collections.synchronizedList(new ArrayList<>());
                List<CompletableFuture<Integer>> futures = new ArrayList<>();

                for (int i = 0; i < 3; i++) {
                    final int val = i;
                    futures.add(limiter.executeScheduled(() -> {
                        completionTimes.add(System.currentTimeMillis());
                        return val * 10;
                    }));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                for (int i = 0; i < 3; i++) {
                    assertEquals(i * 10, futures.get(i).get());
                }
                assertEquals(3, completionTimes.size());
            }
        }

        @Test
        @DisplayName("Rejects task when burst token capacity is exhausted")
        void rejectsWhenTokenExhausted() {
            try (DualRateLimiter limiter = DualRateLimiter.builder()
                    .tokenCapacity(1)
                    .tokenRefillRate(0.01) // slow refill
                    .leakRate(1)
                    .queueCapacity(5)
                    .build()) {

                CompletableFuture<String> f1 = limiter.executeScheduled(() -> "ok");
                CompletableFuture<String> f2 = limiter.executeScheduled(() -> "fail");

                assertTrue(f2.isCompletedExceptionally());
                ExecutionException ex = assertThrows(ExecutionException.class, f2::get);
                assertInstanceOf(RateLimitExceededException.class, ex.getCause());
                assertEquals("TOKEN_BUCKET", ((RateLimitExceededException) ex.getCause()).getStage());
            }
        }

        @Test
        @DisplayName("Rejects and refunds token when leaky bucket queue is full")
        void rejectsWhenQueueFull() {
            try (DualRateLimiter limiter = DualRateLimiter.builder()
                    .tokenCapacity(10)
                    .tokenRefillRate(1)
                    .leakRate(0.1) // very slow leak (1 item per 10s)
                    .queueCapacity(2)
                    .build()) {

                // Queue 2 tasks (fills queue capacity of 2)
                CompletableFuture<String> f1 = limiter.executeScheduled(() -> "t1");
                CompletableFuture<String> f2 = limiter.executeScheduled(() -> "t2");
                CompletableFuture<String> f3 = limiter.executeScheduled(() -> "t3");

                assertTrue(f3.isCompletedExceptionally());
                ExecutionException ex = assertThrows(ExecutionException.class, f3::get);
                assertInstanceOf(RateLimitExceededException.class, ex.getCause());
                assertEquals("LEAKY_QUEUE", ((RateLimitExceededException) ex.getCause()).getStage());
            }
        }
    }

    @Nested
    @DisplayName("Observability and metrics")
    class ObservabilityTests {

        @Test
        @DisplayName("Captures snapshot with correct metrics and delay estimation")
        void snapshotMetrics() {
            try (DualRateLimiter limiter = DualRateLimiter.builder()
                    .tokenCapacity(10)
                    .tokenRefillRate(5)
                    .leakRate(10)
                    .queueCapacity(20)
                    .build()) {

                DualLimiterSnapshot snap = limiter.snapshot();
                assertTrue(snap.availableTokens() <= 10.0 && snap.availableTokens() >= 9.9);
                assertEquals(10.0, snap.tokenCapacity());
                assertEquals(5.0, snap.tokenRefillRate());
                assertEquals(10.0, snap.leakRate());
                assertEquals(20, snap.queueCapacity());
                assertEquals(Duration.ZERO, snap.estimatedQueueDelay());
            }
        }
    }

    @Nested
    @DisplayName("Lifecycle and shutdown")
    class LifecycleTests {

        @Test
        @DisplayName("Rejects new submissions after close()")
        void rejectsAfterClose() {
            DualRateLimiter limiter = DualRateLimiter.builder()
                    .tokenCapacity(5)
                    .tokenRefillRate(5)
                    .leakRate(5)
                    .queueCapacity(5)
                    .build();

            limiter.close();

            assertFalse(limiter.tryAcquire());
            CompletableFuture<String> future = limiter.executeScheduled(() -> "test");
            assertTrue(future.isCompletedExceptionally());
        }
    }
}
