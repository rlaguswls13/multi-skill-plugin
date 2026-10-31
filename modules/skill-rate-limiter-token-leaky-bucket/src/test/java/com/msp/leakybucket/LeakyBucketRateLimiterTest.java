package com.msp.leakybucket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LeakyBucketRateLimiterTest {

    @Test
    @DisplayName("Basic tryAcquire respects bucket capacity")
    void testBasicTryAcquire() {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(3, 10.0);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        // Bucket is full now
        assertFalse(limiter.tryAcquire());

        assertEquals(3, limiter.getCapacity());
        assertEquals(10.0, limiter.getLeakRatePerSecond());
    }

    @Test
    @DisplayName("Bucket leaks drops over time allowing new requests")
    void testLeakingOverTime() throws InterruptedException {
        // Leak rate: 20 drops per sec = 1 drop per 50ms
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(2, 20.0);

        assertTrue(limiter.tryAcquire(2));
        assertFalse(limiter.tryAcquire());

        // Wait ~120ms (should leak ~2 drops)
        Thread.sleep(120);

        assertTrue(limiter.tryAcquire());
    }

    @Test
    @DisplayName("Blocking acquire waits until capacity becomes available")
    void testAcquireBlocking() throws InterruptedException {
        // 10 drops / sec = 1 drop per 100ms
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(1, 10.0);

        assertTrue(limiter.tryAcquire());
        long start = System.currentTimeMillis();

        limiter.acquire(); // Should wait ~100ms
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed >= 80, "Expected at least 80ms wait, got: " + elapsed + "ms");
    }

    @Test
    @DisplayName("Invalid constructor and acquire arguments throw exceptions")
    void testInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucketRateLimiter(0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucketRateLimiter(-1, 10.0));
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucketRateLimiter(10, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucketRateLimiter(10, -5.0));

        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(5, 10.0);
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(-1));
        assertThrows(IllegalArgumentException.class, () -> limiter.acquire(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.acquire(10)); // Exceeds capacity
    }

    @Test
    @DisplayName("Reset clears the bucket water level")
    void testReset() {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(5, 5.0);
        assertTrue(limiter.tryAcquire(5));
        assertFalse(limiter.tryAcquire());

        limiter.reset();
        assertEquals(0, limiter.currentLevel());
        assertTrue(limiter.tryAcquire());
    }

    @Test
    @DisplayName("Concurrent tryAcquire maintains thread-safe level")
    void testConcurrentTryAcquire() throws InterruptedException {
        int threads = 10;
        int requestsPerThread = 50;
        // Capacity 100, low leak rate so practically no leaks during instant burst
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(30, 0.001);

        AtomicInteger acquired = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            CountDownLatch latch = new CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        for (int j = 0; j < requestsPerThread; j++) {
                            if (limiter.tryAcquire()) {
                                acquired.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        // Must never exceed capacity (30)
        assertEquals(30, acquired.get());
        assertTrue(limiter.currentLevel() <= 30 && limiter.currentLevel() >= 29);
    }
}
