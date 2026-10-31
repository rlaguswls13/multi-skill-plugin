package com.msp.ipratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpRateLimiterTest {

    @Test
    @DisplayName("Constructor argument validation")
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new IpRateLimiter(0, 10.0, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new IpRateLimiter(10, 0.0, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new IpRateLimiter(10, 10.0, Duration.ofMillis(-1)));
    }

    @Test
    @DisplayName("Token exhaustion: should exhaust bucket and reject subsequent requests")
    void testTokenExhaustion() {
        AtomicLong currentTimeMillis = new AtomicLong(1000);
        AtomicLong currentNanoTime = new AtomicLong(1_000_000_000L);

        IpRateLimiter limiter = new IpRateLimiter(
                3,
                1.0,
                Duration.ofMinutes(5),
                currentNanoTime::get,
                currentTimeMillis::get
        );

        String ip = "192.168.1.100";
        assertEquals(3, limiter.getAvailableTokens(ip));

        assertTrue(limiter.tryAcquire(ip)); // 2 remaining
        assertTrue(limiter.tryAcquire(ip)); // 1 remaining
        assertTrue(limiter.tryAcquire(ip)); // 0 remaining
        assertFalse(limiter.tryAcquire(ip)); // Rejected!
        assertEquals(0, limiter.getAvailableTokens(ip));
    }

    @Test
    @DisplayName("Refill over time: tokens replenish at configured rate up to capacity")
    void testRefillOverTime() {
        AtomicLong currentTimeMillis = new AtomicLong(1000);
        AtomicLong currentNanoTime = new AtomicLong(1_000_000_000L);

        // Capacity 5, refill rate 2 tokens/sec
        IpRateLimiter limiter = new IpRateLimiter(
                5,
                2.0,
                Duration.ofMinutes(5),
                currentNanoTime::get,
                currentTimeMillis::get
        );

        String ip = "10.0.0.1";
        assertTrue(limiter.tryAcquire(ip, 5)); // Consumes all 5
        assertFalse(limiter.tryAcquire(ip));

        // Advance simulated time by 1 second (1,000,000,000 ns) -> should refill 2 tokens
        currentNanoTime.addAndGet(1_000_000_000L);
        currentTimeMillis.addAndGet(1000L);

        assertEquals(2, limiter.getAvailableTokens(ip));
        assertTrue(limiter.tryAcquire(ip));
        assertTrue(limiter.tryAcquire(ip));
        assertFalse(limiter.tryAcquire(ip));

        // Advance time by 10 seconds -> should cap at capacity 5
        currentNanoTime.addAndGet(10_000_000_000L);
        currentTimeMillis.addAndGet(10_000L);
        assertEquals(5, limiter.getAvailableTokens(ip));
    }

    @Test
    @DisplayName("Multiple IP isolation: IP A cannot deplete IP B's tokens")
    void testMultipleIpIsolation() {
        IpRateLimiter limiter = new IpRateLimiter(2, 1.0, Duration.ofMinutes(1));

        String ipA = "1.1.1.1";
        String ipB = "2.2.2.2";

        assertTrue(limiter.tryAcquire(ipA));
        assertTrue(limiter.tryAcquire(ipA));
        assertFalse(limiter.tryAcquire(ipA), "IP A should be rate limited");

        // IP B must remain unaffected
        assertTrue(limiter.tryAcquire(ipB), "IP B must have available tokens");
        assertTrue(limiter.tryAcquire(ipB));
        assertFalse(limiter.tryAcquire(ipB));
        assertEquals(2, limiter.trackedIpCount());
    }

    @Test
    @DisplayName("Idle IP eviction: cleanupIdleIps removes entries exceeding idleTtl")
    void testIdleIpCleanup() {
        AtomicLong currentTimeMillis = new AtomicLong(10_000L);
        AtomicLong currentNanoTime = new AtomicLong(10_000_000_000L);

        IpRateLimiter limiter = new IpRateLimiter(
                5,
                1.0,
                Duration.ofSeconds(30), // 30 seconds TTL
                currentNanoTime::get,
                currentTimeMillis::get
        );

        limiter.tryAcquire("192.168.1.1");
        limiter.tryAcquire("192.168.1.2");
        assertEquals(2, limiter.trackedIpCount());

        // Advance 10 seconds -> neither is idle yet
        currentTimeMillis.addAndGet(10_000L);
        currentNanoTime.addAndGet(10_000_000_000L);
        assertEquals(0, limiter.cleanupIdleIps());
        assertEquals(2, limiter.trackedIpCount());

        // Touch 192.168.1.1 at t=20s
        limiter.tryAcquire("192.168.1.1");

        // Advance to t=45s (IP 2 last accessed at t=10s -> idle 35s > 30s TTL, IP 1 last accessed at t=20s -> idle 25s < 30s)
        currentTimeMillis.set(45_000L);
        currentNanoTime.set(45_000_000_000L);

        int evicted = limiter.cleanupIdleIps();
        assertEquals(1, evicted, "Only 192.168.1.2 should be evicted");
        assertEquals(1, limiter.trackedIpCount());

        // Advance to t=60s -> IP 1 idle time is 40s > 30s -> evicted
        currentTimeMillis.set(60_000L);
        currentNanoTime.set(60_000_000_000L);
        assertEquals(1, limiter.cleanupIdleIps());
        assertEquals(0, limiter.trackedIpCount());
    }

    @Test
    @DisplayName("Reset and Clear methods work as expected")
    void testResetAndClear() {
        IpRateLimiter limiter = new IpRateLimiter(5, 1.0, Duration.ofMinutes(5));
        String ip = "172.16.0.1";

        assertTrue(limiter.tryAcquire(ip, 5));
        assertEquals(0, limiter.getAvailableTokens(ip));

        limiter.reset(ip);
        assertEquals(5, limiter.getAvailableTokens(ip));

        limiter.clear();
        assertEquals(0, limiter.trackedIpCount());
    }

    @Test
    @DisplayName("Concurrency safety: multiple threads competing for tokens never exceed capacity")
    void testConcurrencySafety() throws InterruptedException {
        int capacity = 100;
        // refill = 0.0001 so virtually no tokens refill during the burst test
        IpRateLimiter limiter = new IpRateLimiter(capacity, 0.0001, Duration.ofMinutes(1));
        String ip = "10.10.10.10";

        int threadCount = 20;
        int requestsPerThread = 10; // Total 200 requests for 100 tokens
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger acquiredCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < requestsPerThread; j++) {
                        if (limiter.tryAcquire(ip)) {
                            acquiredCount.incrementAndGet();
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

        assertEquals(capacity, acquiredCount.get(), "Exactly capacity tokens must be granted without over-allocation under concurrent load");
        assertEquals(0, limiter.getAvailableTokens(ip));
    }
}
