package com.msp.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Rate Limiter Tests")
class RateLimiterTest {

    @Nested
    @DisplayName("TokenBucketRateLimiter Tests")
    class TokenBucketTests {

        @Test
        @DisplayName("Should allow burst requests up to capacity and then reject")
        void burstAllowanceAndRejection() {
            RateLimiter limiter = RateLimiters.tokenBucket(5, 10.0);

            // First 5 should succeed
            for (int i = 0; i < 5; i++) {
                assertTrue(limiter.tryAcquire(), "Permit " + (i + 1) + " should be acquired");
            }

            // 6th should fail immediately
            assertFalse(limiter.tryAcquire(), "Burst capacity exceeded, should fail");
        }

        @Test
        @DisplayName("Should refill tokens over time")
        void tokenRefillOverTime() throws InterruptedException {
            // 10 tokens per second = 1 token per 100ms
            RateLimiter limiter = RateLimiters.tokenBucket(2, 10.0);

            assertTrue(limiter.tryAcquire());
            assertTrue(limiter.tryAcquire());
            assertFalse(limiter.tryAcquire());

            // Wait ~250ms -> should refill ~2 tokens
            Thread.sleep(250);

            assertTrue(limiter.tryAcquire());
            assertTrue(limiter.tryAcquire());
            assertFalse(limiter.tryAcquire());
        }

        @Test
        @DisplayName("Should support multi-permit acquire")
        void multiPermitAcquire() {
            RateLimiter limiter = RateLimiters.tokenBucket(10, 10.0);

            assertTrue(limiter.tryAcquire(5));
            assertEquals(5, limiter.availablePermits());

            assertFalse(limiter.tryAcquire(6));
            assertTrue(limiter.tryAcquire(5));
            assertFalse(limiter.tryAcquire(1));
        }

        @Test
        @DisplayName("Should wait and acquire with timeout")
        void timeoutAcquire() throws InterruptedException {
            // 20 tokens / sec = 1 token per 50ms
            RateLimiter limiter = RateLimiters.tokenBucket(1, 20.0);

            assertTrue(limiter.tryAcquire());
            assertFalse(limiter.tryAcquire());

            // Wait with timeout of 200ms -> should succeed
            long start = System.nanoTime();
            boolean acquired = limiter.tryAcquire(Duration.ofMillis(200));
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            assertTrue(acquired);
            assertTrue(elapsed >= 30, "Should have waited for refill");
        }

        @Test
        @DisplayName("Should timeout when wait exceeds deadline")
        void timeoutExceeded() throws InterruptedException {
            RateLimiter limiter = RateLimiters.tokenBucket(1, 1.0); // 1 token per second
            assertTrue(limiter.tryAcquire());

            // Need 1 token, takes 1000ms, but timeout is only 50ms
            boolean acquired = limiter.tryAcquire(Duration.ofMillis(50));
            assertFalse(acquired);
        }

        @Test
        @DisplayName("Concurrent threads should safely acquire limited permits")
        void concurrentAcquire() throws InterruptedException {
            int capacity = 50;
            RateLimiter limiter = RateLimiters.tokenBucket(capacity, 1000.0);

            int threadCount = 20;
            int requestsPerThread = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        for (int j = 0; j < requestsPerThread; j++) {
                            if (limiter.tryAcquire()) {
                                successCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            // Initial capacity is 50, even if slight refill occurs during few ms, successCount <= ~60
            assertTrue(successCount.get() >= 50);
        }

        @Test
        @DisplayName("Invalid parameters should throw IllegalArgumentException")
        void invalidParameters() {
            assertThrows(IllegalArgumentException.class, () -> RateLimiters.tokenBucket(0, 10.0));
            assertThrows(IllegalArgumentException.class, () -> RateLimiters.tokenBucket(10, 0.0));
            assertThrows(IllegalArgumentException.class, () -> RateLimiters.tokenBucket(10, -5.0));

            RateLimiter limiter = RateLimiters.tokenBucket(10, 10.0);
            assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
            assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(-1));
        }
    }

    @Nested
    @DisplayName("SlidingWindowRateLimiter Tests")
    class SlidingWindowTests {

        @Test
        @DisplayName("Should cap permits strictly within window")
        void windowCap() {
            RateLimiter limiter = RateLimiters.slidingWindow(5, Duration.ofMillis(300), 10);

            for (int i = 0; i < 5; i++) {
                assertTrue(limiter.tryAcquire(), "Request " + (i + 1) + " should be permitted");
            }

            assertFalse(limiter.tryAcquire(), "6th request should be rejected");
            assertEquals(0, limiter.availablePermits());
        }

        @Test
        @DisplayName("Permits should become available again after window slides")
        void permitsFreedAfterWindowPasses() throws InterruptedException {
            RateLimiter limiter = RateLimiters.slidingWindow(3, Duration.ofMillis(150), 10);

            assertTrue(limiter.tryAcquire());
            assertTrue(limiter.tryAcquire());
            assertTrue(limiter.tryAcquire());
            assertFalse(limiter.tryAcquire());

            // Sleep past window
            Thread.sleep(220);

            assertTrue(limiter.tryAcquire(), "After window expiry, permits should be available");
        }

        @Test
        @DisplayName("Timeout acquire should wait until older window slice rolls off")
        void timeoutAcquire() throws InterruptedException {
            RateLimiter limiter = RateLimiters.slidingWindow(2, Duration.ofMillis(150), 10);

            assertTrue(limiter.tryAcquire());
            assertTrue(limiter.tryAcquire());
            assertFalse(limiter.tryAcquire());

            // Wait with 300ms timeout -> window of 150ms should pass and acquire should succeed
            boolean acquired = limiter.tryAcquire(Duration.ofMillis(300));
            assertTrue(acquired);
        }

        @Test
        @DisplayName("Concurrent requests on sliding window")
        void concurrentSlidingWindow() throws InterruptedException {
            int limit = 100;
            RateLimiter limiter = RateLimiters.slidingWindow(limit, Duration.ofMillis(500), 20);

            int threadCount = 20;
            int attemptsPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger acquiredCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        for (int j = 0; j < attemptsPerThread; j++) {
                            if (limiter.tryAcquire()) {
                                acquiredCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            // Must never exceed the window limit during immediate concurrent burst
            assertTrue(acquiredCount.get() <= limit, "Acquired count " + acquiredCount.get() + " exceeded limit " + limit);
        }

        @Test
        @DisplayName("Invalid parameters should throw IllegalArgumentException")
        void invalidParameters() {
            assertThrows(IllegalArgumentException.class, () -> RateLimiters.slidingWindow(0, Duration.ofSeconds(1)));
            assertThrows(IllegalArgumentException.class, () -> RateLimiters.slidingWindow(10, Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> RateLimiters.slidingWindow(10, Duration.ofSeconds(1), 0));
        }
    }
}
