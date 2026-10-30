package com.msp.ipratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class IpRateLimiterTest {

    @Test
    @DisplayName("IP rate limiter allows requests up to capacity and rejects overflow")
    void testRateLimit() {
        IpRateLimiter limiter = IpRateLimiter.of(3, Duration.ofSeconds(10));
        String ip1 = "192.168.1.100";
        String ip2 = "192.168.1.200";

        // IP 1: 3 permits allowed
        assertTrue(limiter.tryAcquire(ip1).allowed());
        assertTrue(limiter.tryAcquire(ip1).allowed());
        assertTrue(limiter.tryAcquire(ip1).allowed());

        // 4th request from IP 1 rejected
        IpRateLimitResult rejected = limiter.tryAcquire(ip1);
        assertFalse(rejected.allowed());
        assertTrue(rejected.retryAfter().toMillis() > 0);

        // IP 2 is isolated and still has full permits
        assertTrue(limiter.tryAcquire(ip2).allowed());
        assertEquals(2, limiter.trackedIpCount());
    }

    @Test
    @DisplayName("Cleanup removes idle IP buckets")
    void testCleanupExpired() throws Exception {
        IpRateLimiter limiter = IpRateLimiter.of(5, Duration.ofMillis(20));
        limiter.tryAcquire("10.0.0.1");
        assertEquals(1, limiter.trackedIpCount());

        Thread.sleep(60); // wait longer than window * 2 (40ms)
        int removed = limiter.cleanupExpired();
        assertEquals(1, removed);
        assertEquals(0, limiter.trackedIpCount());
    }
}
