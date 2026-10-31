package com.msp.gcra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GcraRateLimiterTest {

    @Test
    @DisplayName("Single acquire with no burst tolerance conforms to emission interval")
    void testSingleAcquireNoBurst() {
        // 1 request every 50ms, 0 burst tolerance
        GcraRateLimiter limiter = new GcraRateLimiter(Duration.ofMillis(50), Duration.ZERO);

        assertTrue(limiter.tryAcquire(), "First request should succeed");
        assertFalse(limiter.tryAcquire(), "Immediate second request should fail");

        Duration waitTime = limiter.getWaitTime();
        assertTrue(waitTime.toMillis() > 0, "Wait time should be positive");
    }

    @Test
    @DisplayName("Burst tolerance allows consecutive requests up to capacity")
    void testBurstTolerance() {
        // Interval = 50ms, burstTolerance = 150ms -> permits burst of 4 requests (1 initial + 3 burst)
        GcraRateLimiter limiter = new GcraRateLimiter(Duration.ofMillis(50), Duration.ofMillis(150));

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "5th immediate request should be rejected");

        // Wait time should be around 50ms
        Duration wait = limiter.getWaitTime();
        assertTrue(wait.toMillis() >= 20 && wait.toMillis() <= 60, "Wait time should be around 50ms, got: " + wait.toMillis());
    }

    @Test
    @DisplayName("fromRate factory creates expected burst capacity")
    void testFromRateFactory() {
        // 10 req/s with maxBurst = 3
        GcraRateLimiter limiter = GcraRateLimiter.fromRate(10.0, 3);
        assertEquals(Duration.ofMillis(100), limiter.getEmissionInterval());

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "4th immediate request exceeds burst of 3");
    }

    @Test
    @DisplayName("Batch acquire tryAcquire(count)")
    void testBatchAcquire() {
        // 10 req/s, burst 5
        GcraRateLimiter limiter = GcraRateLimiter.fromRate(10.0, 5);

        // Can acquire 3
        assertTrue(limiter.tryAcquire(3));
        // Can acquire 2 more
        assertTrue(limiter.tryAcquire(2));
        // Next 1 should fail
        assertFalse(limiter.tryAcquire(1));

        // Trying to acquire 10 at once should fail
        limiter.reset();
        assertFalse(limiter.tryAcquire(6));
        assertTrue(limiter.tryAcquire(5));
    }

    @Test
    @DisplayName("Reset clears limiter state")
    void testReset() {
        GcraRateLimiter limiter = new GcraRateLimiter(Duration.ofSeconds(1), Duration.ZERO);
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        limiter.reset();
        assertEquals(Duration.ZERO, limiter.getWaitTime());
        assertTrue(limiter.tryAcquire(), "Should succeed immediately after reset");
    }

    @Test
    @DisplayName("Concurrent tryAcquire across multiple threads")
    void testConcurrentAcquire() throws InterruptedException {
        int burst = 20;
        GcraRateLimiter limiter = GcraRateLimiter.fromRate(1.0, burst); // 1 req/sec, burst 20

        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (limiter.tryAcquire()) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Exactly 20 permits should be granted
        assertEquals(burst, successCount.get());
    }

    @Test
    @DisplayName("Invalid parameters throw IllegalArgumentException")
    void testValidation() {
        assertThrows(NullPointerException.class, () -> new GcraRateLimiter(null, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> new GcraRateLimiter(Duration.ofSeconds(1), null));
        assertThrows(IllegalArgumentException.class, () -> new GcraRateLimiter(Duration.ZERO, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new GcraRateLimiter(Duration.ofMillis(-1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new GcraRateLimiter(Duration.ofMillis(10), Duration.ofMillis(-5)));

        GcraRateLimiter limiter = new GcraRateLimiter(Duration.ofMillis(10), Duration.ofMillis(10));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(-2));
        assertThrows(IllegalArgumentException.class, () -> GcraRateLimiter.fromRate(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> GcraRateLimiter.fromRate(10, 0));
    }
}
