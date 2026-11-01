package com.msp.dynamiclimiter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DynamicWindowRateLimiter Tests")
class DynamicWindowRateLimiterTest {

    @Test
    @DisplayName("Permit consumption in steady state within single window")
    void testPermitConsumption() {
        DynamicWindowRateLimiter limiter = new DynamicWindowRateLimiter(5, 50, 10, Duration.ofMinutes(1));

        assertEquals(10, limiter.getCurrentPermits());
        assertEquals(10, limiter.getAvailablePermitsInWindow());

        // Acquire 5 permits
        assertTrue(limiter.tryAcquire(5));
        assertEquals(5, limiter.getAvailablePermitsInWindow());

        // Acquire 5 more permits
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire());
        }
        assertEquals(0, limiter.getAvailablePermitsInWindow());

        // Next acquire should fail
        assertFalse(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(1));
    }

    @Test
    @DisplayName("AIMD increase on healthy feedback")
    void testAdditiveIncrease() {
        DynamicWindowRateLimiter limiter = new DynamicWindowRateLimiter(5, 15, 10, Duration.ofSeconds(10), 2, 0.5);

        // Success within target latency -> increases by 2
        limiter.recordFeedback(true, Duration.ofMillis(20), Duration.ofMillis(50));
        assertEquals(12, limiter.getCurrentPermits());

        limiter.recordFeedback(true, Duration.ofMillis(10), Duration.ofMillis(50));
        assertEquals(14, limiter.getCurrentPermits());

        // Next increase should be capped at maxPermits (15)
        limiter.recordFeedback(true, Duration.ofMillis(30), Duration.ofMillis(50));
        assertEquals(15, limiter.getCurrentPermits());

        limiter.recordFeedback(true, Duration.ofMillis(10), Duration.ofMillis(50));
        assertEquals(15, limiter.getCurrentPermits());
    }

    @Test
    @DisplayName("Multiplicative decrease on failure or high latency")
    void testMultiplicativeDecrease() {
        DynamicWindowRateLimiter limiter = new DynamicWindowRateLimiter(4, 20, 16, Duration.ofSeconds(10), 1, 0.5);

        // Failure feedback -> decreases by factor 0.5 (16 -> 8)
        limiter.recordFeedback(false, Duration.ofMillis(10), Duration.ofMillis(50));
        assertEquals(8, limiter.getCurrentPermits());

        // High latency feedback (> targetLatency) -> decreases (8 -> 4)
        limiter.recordFeedback(true, Duration.ofMillis(100), Duration.ofMillis(50));
        assertEquals(4, limiter.getCurrentPermits());

        // Bound protection: should not fall below minPermits (4)
        limiter.recordFeedback(false, Duration.ofMillis(200), Duration.ofMillis(50));
        assertEquals(4, limiter.getCurrentPermits());
    }

    @Test
    @DisplayName("Direct update and reset behavior")
    void testDirectUpdateAndReset() {
        DynamicWindowRateLimiter limiter = new DynamicWindowRateLimiter(5, 50, 20, Duration.ofSeconds(30));

        limiter.updatePermitsDirectly(35);
        assertEquals(35, limiter.getCurrentPermits());

        // Clamping bounds
        limiter.updatePermitsDirectly(100);
        assertEquals(50, limiter.getCurrentPermits());

        limiter.updatePermitsDirectly(2);
        assertEquals(5, limiter.getCurrentPermits());

        // Consume permits then reset
        assertTrue(limiter.tryAcquire(5));
        assertEquals(0, limiter.getAvailablePermitsInWindow());

        limiter.reset();
        assertEquals(20, limiter.getCurrentPermits());
        assertEquals(20, limiter.getAvailablePermitsInWindow());
    }

    @Test
    @DisplayName("Window rollover restores permits")
    void testWindowRollover() throws InterruptedException {
        DynamicWindowRateLimiter limiter = new DynamicWindowRateLimiter(2, 10, 5, Duration.ofMillis(50));

        assertTrue(limiter.tryAcquire(5));
        assertFalse(limiter.tryAcquire());

        // Wait for window to expire
        Thread.sleep(70);

        // Permits should be renewed in new window
        assertTrue(limiter.tryAcquire(3));
        assertEquals(2, limiter.getAvailablePermitsInWindow());
    }

    @Test
    @DisplayName("Multi-thread burst safety: strict permit cap under concurrency")
    void testMultiThreadSafety() throws InterruptedException {
        int capacity = 50;
        DynamicWindowRateLimiter limiter = new DynamicWindowRateLimiter(10, 100, capacity, Duration.ofMinutes(1));

        int threadCount = 20;
        int requestsPerThread = 10; // Total 200 requests attempted
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successfulAcquires = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < requestsPerThread; j++) {
                        if (limiter.tryAcquire()) {
                            successfulAcquires.incrementAndGet();
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
        doneLatch.await();
        executor.shutdown();

        // Total successful acquires must NEVER exceed capacity
        assertEquals(capacity, successfulAcquires.get());
        assertEquals(0, limiter.getAvailablePermitsInWindow());
    }

    @Test
    @DisplayName("Constructor and parameter validation checks")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                new DynamicWindowRateLimiter(0, 10, 5, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () ->
                new DynamicWindowRateLimiter(10, 5, 5, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () ->
                new DynamicWindowRateLimiter(5, 10, 20, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () ->
                new DynamicWindowRateLimiter(5, 10, 7, Duration.ZERO));
        assertThrows(NullPointerException.class, () ->
                new DynamicWindowRateLimiter(5, 10, 7, null));

        DynamicWindowRateLimiter limiter = new DynamicWindowRateLimiter(5, 20, 10, Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(-1));
        assertThrows(NullPointerException.class, () ->
                limiter.recordFeedback(true, null, Duration.ofMillis(10)));
    }
}
