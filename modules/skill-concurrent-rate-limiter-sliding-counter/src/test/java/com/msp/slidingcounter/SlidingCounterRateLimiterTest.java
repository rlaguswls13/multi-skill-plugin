package com.msp.slidingcounter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SlidingCounterRateLimiterTest {

    @Test
    @DisplayName("Limit enforcement: permits up to maxRequests then rejects")
    void testLimitEnforcement() {
        AtomicLong virtualTime = new AtomicLong(1_000_000L);
        // Window 1000ms, 10 slots (100ms per slot), max 5 requests
        SlidingCounterRateLimiter limiter = new SlidingCounterRateLimiter(5, Duration.ofMillis(1000), 10, virtualTime::get);

        assertEquals(0, limiter.getCurrentCount());

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire(2));
        assertTrue(limiter.tryAcquire(2));
        assertEquals(5, limiter.getCurrentCount());

        // 6th permit should be rejected
        assertFalse(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(1));
        assertEquals(5, limiter.getCurrentCount());
    }

    @Test
    @DisplayName("Sliding expiration restores capacity smoothly across slot transitions")
    void testSlidingExpirationAcrossSlotTransitions() {
        AtomicLong virtualTime = new AtomicLong(0L);
        // 1000ms window, 10 slots = 100ms per slot. Max 10 requests.
        SlidingCounterRateLimiter limiter = new SlidingCounterRateLimiter(10, Duration.ofMillis(1000), 10, virtualTime::get);

        // At t=0ms, acquire 4 permits
        assertTrue(limiter.tryAcquire(4));
        assertEquals(4, limiter.getCurrentCount());

        // Advance to t=200ms (slot 2), acquire 6 permits (total 10 reached)
        virtualTime.set(200L);
        assertTrue(limiter.tryAcquire(6));
        assertEquals(10, limiter.getCurrentCount());
        assertFalse(limiter.tryAcquire(1)); // limit reached

        // Advance to t=950ms (still within 1000ms of slot 0): count should still be 10
        virtualTime.set(950L);
        assertEquals(10, limiter.getCurrentCount());
        assertFalse(limiter.tryAcquire(1));

        // Advance to t=1050ms: slot 0 (t=0..99ms) has now expired! (currentSlot = 10, window covers slots 1..10)
        virtualTime.set(1050L);
        // Slot 0 (4 permits) expired, so current count is now 6
        assertEquals(6, limiter.getCurrentCount());

        // We can now acquire 4 new permits!
        assertTrue(limiter.tryAcquire(4));
        assertEquals(10, limiter.getCurrentCount());
        assertFalse(limiter.tryAcquire(1));

        // Advance to t=1250ms: slot 2 (which had 6 permits) has now also expired!
        virtualTime.set(1250L);
        // Remaining count is only the 4 permits acquired at t=1050ms
        assertEquals(4, limiter.getCurrentCount());
        assertTrue(limiter.tryAcquire(6));
        assertEquals(10, limiter.getCurrentCount());
    }

    @Test
    @DisplayName("High burst concurrency with multi-threaded attempts enforces strict limit")
    void testBurstConcurrency() throws Exception {
        int threads = 16;
        int attemptsPerThread = 500;
        long maxRequests = 200;

        // Window of 5 seconds, 10 slots
        SlidingCounterRateLimiter limiter = new SlidingCounterRateLimiter(maxRequests, Duration.ofSeconds(5), 10);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger totalSuccess = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (limiter.tryAcquire()) {
                            totalSuccess.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(maxRequests, totalSuccess.get(), "Total acquired permits must exactly match maxRequests");
        assertEquals(maxRequests, limiter.getCurrentCount());
    }

    @Test
    @DisplayName("Invalid parameters throw IllegalArgumentException")
    void testInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingCounterRateLimiter(0, Duration.ofSeconds(1), 10));
        assertThrows(IllegalArgumentException.class, () -> new SlidingCounterRateLimiter(-5, Duration.ofSeconds(1), 10));
        assertThrows(IllegalArgumentException.class, () -> new SlidingCounterRateLimiter(10, Duration.ZERO, 10));
        assertThrows(IllegalArgumentException.class, () -> new SlidingCounterRateLimiter(10, Duration.ofSeconds(-1), 10));
        assertThrows(IllegalArgumentException.class, () -> new SlidingCounterRateLimiter(10, Duration.ofSeconds(1), 0));
        assertThrows(IllegalArgumentException.class, () -> new SlidingCounterRateLimiter(10, Duration.ofSeconds(1), -1));
        assertThrows(IllegalArgumentException.class, () -> new SlidingCounterRateLimiter(10, Duration.ofMillis(5), 10)); // 5ms < 10 slots

        SlidingCounterRateLimiter limiter = new SlidingCounterRateLimiter(10, Duration.ofSeconds(1), 10);
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(-1));
        assertFalse(limiter.tryAcquire(100)); // permits > maxRequests returns false directly
    }
}
