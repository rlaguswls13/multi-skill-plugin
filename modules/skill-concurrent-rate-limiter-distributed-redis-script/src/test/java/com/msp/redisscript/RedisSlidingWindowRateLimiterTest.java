package com.msp.redisscript;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RedisSlidingWindowRateLimiterTest {

    private RedisSlidingWindowRateLimiterEmulator limiter;

    @BeforeEach
    void setUp() {
        limiter = new RedisSlidingWindowRateLimiterEmulator();
    }

    @Test
    @DisplayName("Allows requests up to limit and calculates remaining tokens")
    void testBasicRateLimiting() {
        String key = "test-key";
        Instant t0 = Instant.parse("2026-11-01T12:00:00Z");
        Duration window = Duration.ofSeconds(10);
        int limit = 3;

        // 1st request
        RateLimitDecision d1 = limiter.tryAcquire(key, limit, window, t0, "req-1");
        assertTrue(d1.allowed());
        assertEquals(2, d1.remainingTokens());
        assertEquals(0, d1.retryAfterMillis());

        // 2nd request
        RateLimitDecision d2 = limiter.tryAcquire(key, limit, window, t0.plusMillis(1000), "req-2");
        assertTrue(d2.allowed());
        assertEquals(1, d2.remainingTokens());

        // 3rd request
        RateLimitDecision d3 = limiter.tryAcquire(key, limit, window, t0.plusMillis(2000), "req-3");
        assertTrue(d3.allowed());
        assertEquals(0, d3.remainingTokens());

        // 4th request (should be rejected)
        RateLimitDecision d4 = limiter.tryAcquire(key, limit, window, t0.plusMillis(3000), "req-4");
        assertFalse(d4.allowed());
        assertEquals(0, d4.remainingTokens());
        // Oldest request was at t0, window is 10s, so reset is t0 + 10s = 10,000ms.
        // Current is t0 + 3s = 3000ms. Retry-after should be 7000ms.
        assertEquals(7000, d4.retryAfterMillis());
        assertEquals(7.0, d4.retryAfterSeconds());
    }

    @Test
    @DisplayName("Sliding window restores token as oldest request expires")
    void testSlidingWindowRestoration() {
        String key = "slide-key";
        Instant t0 = Instant.parse("2026-11-01T12:00:00Z");
        Duration window = Duration.ofSeconds(5);
        int limit = 2;

        limiter.tryAcquire(key, limit, window, t0, "m1");
        limiter.tryAcquire(key, limit, window, t0.plusSeconds(2), "m2");

        // Reject at +3s
        RateLimitDecision dFail = limiter.tryAcquire(key, limit, window, t0.plusSeconds(3), "m3");
        assertFalse(dFail.allowed());

        // Advance to +5.1s (m1 has expired since t0 + 5s < t0 + 5.1s)
        RateLimitDecision dSuccess = limiter.tryAcquire(key, limit, window, t0.plusMillis(5100), "m4");
        assertTrue(dSuccess.allowed());
        assertEquals(0, dSuccess.remainingTokens()); // m2 and m4 are now in window
    }

    @Test
    @DisplayName("Check does not consume rate limit tokens")
    void testCheckDoesNotConsume() {
        String key = "check-key";
        Instant t0 = Instant.now();
        Duration window = Duration.ofSeconds(10);

        RateLimitDecision check1 = limiter.check(key, 5, window, t0);
        assertTrue(check1.allowed());
        assertEquals(5, check1.remainingTokens());

        // Still 0 active requests
        assertEquals(0, limiter.getActiveCount(key, window, t0));
    }

    @Test
    @DisplayName("Concurrent execution: exactly matches limit under parallel load")
    void testConcurrency() throws InterruptedException {
        String key = "concurrent-user";
        int limit = 15;
        Duration window = Duration.ofSeconds(10);
        int threadCount = 30;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();

        Instant fixedNow = Instant.now();

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RateLimitDecision decision = limiter.tryAcquire(key, limit, window, fixedNow, "worker-" + id);
                    if (decision.allowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(15, allowedCount.get());
        assertEquals(15, rejectedCount.get());
    }

    @Test
    @DisplayName("Lua script is well-formed, produces valid SHA-1 and argument list")
    void testRedisLuaScript() {
        String script = RedisRateLimiterScript.getLuaScript();
        assertNotNull(script);
        assertTrue(script.contains("ZREMRANGEBYSCORE"));
        assertTrue(script.contains("ZCARD"));
        assertTrue(script.contains("ZADD"));
        assertTrue(script.contains("PEXPIRE"));
        assertTrue(script.contains("ZRANGE"));

        String sha1 = RedisRateLimiterScript.getSha1();
        assertNotNull(sha1);
        assertEquals(40, sha1.length());

        List<String> args = RedisRateLimiterScript.buildArgs(1000L, 5000L, 10L, "uuid-123");
        assertEquals(4, args.size());
        assertEquals("1000", args.get(0));
        assertEquals("5000", args.get(1));
        assertEquals("10", args.get(2));
        assertEquals("uuid-123", args.get(3));
    }

    @Test
    @DisplayName("Parses Redis response format properly")
    void testRedisResponseParsing() {
        // Allowed response: [1, 4, 0, 1600000000000]
        RateLimitDecision allowed = RedisRateLimiterScript.parseResponse(List.of(1L, 4L, 0L, 1600000000000L));
        assertTrue(allowed.allowed());
        assertEquals(4, allowed.remainingTokens());
        assertEquals(0, allowed.retryAfterMillis());
        assertEquals(1600000000000L, allowed.resetTimeEpochMs());

        // Rejected response: [0, 0, 2500, 1600000002500]
        RateLimitDecision rejected = RedisRateLimiterScript.parseResponse(List.of(0L, 0L, 2500L, 1600000002500L));
        assertFalse(rejected.allowed());
        assertEquals(0, rejected.remainingTokens());
        assertEquals(2500, rejected.retryAfterMillis());
        assertEquals(2.5, rejected.retryAfterSeconds());
    }

    @Test
    @DisplayName("Reset and clear removes keys")
    void testReset() {
        limiter.tryAcquire("key1", 5, Duration.ofSeconds(10));
        limiter.tryAcquire("key2", 5, Duration.ofSeconds(10));
        assertEquals(2, limiter.getTrackedKeyCount());

        limiter.reset("key1");
        assertEquals(1, limiter.getTrackedKeyCount());

        limiter.clear();
        assertEquals(0, limiter.getTrackedKeyCount());
    }
}
