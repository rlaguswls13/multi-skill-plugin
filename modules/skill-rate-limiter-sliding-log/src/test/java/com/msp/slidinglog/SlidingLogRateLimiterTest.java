package com.msp.slidinglog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SlidingLogRateLimiterTest {

    static final class MockTimeSource implements SlidingLogRateLimiter.TimeSource {
        private final AtomicLong currentMicros = new AtomicLong(1_000_000L);

        @Override
        public long getMicros() {
            return currentMicros.get();
        }

        public void advance(Duration duration) {
            currentMicros.addAndGet(duration.toNanos() / 1_000L);
        }

        public void advanceMicros(long micros) {
            currentMicros.addAndGet(micros);
        }
    }

    @Test
    @DisplayName("Allows up to maxRequests and blocks excess requests within window")
    void testBasicRateLimit() {
        MockTimeSource timeSource = new MockTimeSource();
        SlidingLogRateLimiter limiter = new SlidingLogRateLimiter(timeSource);

        Duration window = Duration.ofSeconds(1);
        int maxRequests = 3;

        assertTrue(limiter.tryAcquire(maxRequests, window), "Req 1 should be allowed");
        assertTrue(limiter.tryAcquire(maxRequests, window), "Req 2 should be allowed");
        assertTrue(limiter.tryAcquire(maxRequests, window), "Req 3 should be allowed");
        assertFalse(limiter.tryAcquire(maxRequests, window), "Req 4 should be rejected");

        assertEquals(0, limiter.getRemainingPermits(maxRequests, window));
    }

    @Test
    @DisplayName("Permits replenish continuously as timestamps slide out of window")
    void testSlidingWindowReplenish() {
        MockTimeSource timeSource = new MockTimeSource();
        SlidingLogRateLimiter limiter = new SlidingLogRateLimiter(timeSource);

        Duration window = Duration.ofSeconds(10);
        int max = 2;

        // t=0s: Req 1
        assertTrue(limiter.tryAcquire("client-1", max, window));

        // t=4s: Req 2
        timeSource.advance(Duration.ofSeconds(4));
        assertTrue(limiter.tryAcquire("client-1", max, window));

        // t=4s: Req 3 -> reject
        assertFalse(limiter.tryAcquire("client-1", max, window));

        // t=10.5s: Req 1 (t=0s) slides out of 10s window, Req 2 (t=4s) is still inside
        timeSource.advance(Duration.ofMillis(6500));
        assertEquals(1, limiter.getRemainingPermits("client-1", max, window));
        assertTrue(limiter.tryAcquire("client-1", max, window), "Permit from Req 1 should have opened up");
        assertFalse(limiter.tryAcquire("client-1", max, window), "Still at max limit");

        // t=14.5s: Req 2 (t=4s) slides out
        timeSource.advance(Duration.ofSeconds(4));
        assertEquals(1, limiter.getRemainingPermits("client-1", max, window));
        assertTrue(limiter.tryAcquire("client-1", max, window));
    }

    @Test
    @DisplayName("Independent partition keys do not affect each other")
    void testKeyPartitioning() {
        MockTimeSource timeSource = new MockTimeSource();
        SlidingLogRateLimiter limiter = new SlidingLogRateLimiter(timeSource);

        Duration window = Duration.ofSeconds(1);
        int max = 2;

        assertTrue(limiter.tryAcquire("user-A", max, window));
        assertTrue(limiter.tryAcquire("user-A", max, window));
        assertFalse(limiter.tryAcquire("user-A", max, window));

        // user-B has independent quota
        assertTrue(limiter.tryAcquire("user-B", max, window));
        assertTrue(limiter.tryAcquire("user-B", max, window));
        assertFalse(limiter.tryAcquire("user-B", max, window));
    }

    @Test
    @DisplayName("Eviction of stale key logs removes inactive partitions")
    void testStaleEviction() {
        MockTimeSource timeSource = new MockTimeSource();
        SlidingLogRateLimiter limiter = new SlidingLogRateLimiter(timeSource);

        limiter.tryAcquire("active-user", 5, Duration.ofSeconds(1));
        limiter.tryAcquire("idle-user", 5, Duration.ofSeconds(1));

        assertEquals(2, limiter.getActiveKeyCount());

        // Advance 20 seconds
        timeSource.advance(Duration.ofSeconds(20));

        // Only active user has activity now
        limiter.tryAcquire("active-user", 5, Duration.ofSeconds(1));

        // Evict keys idle for > 10 seconds
        int evicted = limiter.evictStale(Duration.ofSeconds(10));
        assertEquals(1, evicted);
        assertEquals(1, limiter.getActiveKeyCount());
    }

    @Test
    @DisplayName("Resetting key or all keys clears limits")
    void testReset() {
        SlidingLogRateLimiter limiter = new SlidingLogRateLimiter();
        limiter.tryAcquire("user-1", 1, Duration.ofMinutes(1));
        assertFalse(limiter.tryAcquire("user-1", 1, Duration.ofMinutes(1)));

        limiter.reset("user-1");
        assertTrue(limiter.tryAcquire("user-1", 1, Duration.ofMinutes(1)));

        limiter.tryAcquire("user-2", 1, Duration.ofMinutes(1));
        limiter.resetAll();
        assertEquals(0, limiter.getActiveKeyCount());
    }

    @Test
    @DisplayName("Concurrent requests strictly honor rate limit")
    void testConcurrentAcquisitions() throws InterruptedException {
        SlidingLogRateLimiter limiter = new SlidingLogRateLimiter();
        int threads = 20;
        int maxRequests = 10;
        Duration window = Duration.ofSeconds(5);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    if (limiter.tryAcquire("concurrent-key", maxRequests, window)) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(maxRequests, successCount.get(), "Must allow exactly maxRequests even under heavy concurrency");
    }

    @Test
    @DisplayName("Invalid parameters throw IllegalArgumentException")
    void testInvalidParameters() {
        SlidingLogRateLimiter limiter = new SlidingLogRateLimiter();
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire("key", -5, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire("key", 5, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> limiter.tryAcquire(null, 5, Duration.ofSeconds(1)));
    }
}
