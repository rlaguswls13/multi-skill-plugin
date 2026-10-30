package com.msp.ipratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 클라이언트 IP별로 윈도우 처리율을 개별 격리 추적하며
 * 만료된 비활성 IP를 자동/수동 정리하여 메모리 누수를 방지하는 멀티테넌트 IP Rate Limiter.
 */
public final class IpRateLimiter {

    private static final class IpBucket {
        final long capacity;
        final double refillRatePerMs;
        double tokens;
        long lastRefillTimestamp;

        IpBucket(long capacity, Duration window) {
            this.capacity = capacity;
            this.refillRatePerMs = (double) capacity / window.toMillis();
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        synchronized IpRateLimitResult tryAcquire(long permits, Duration window) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTimestamp;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + (elapsed * refillRatePerMs));
                lastRefillTimestamp = now;
            }

            if (tokens >= permits) {
                tokens -= permits;
                return IpRateLimitResult.allowed((long) tokens);
            } else {
                double needed = permits - tokens;
                long waitMs = (long) Math.ceil(needed / refillRatePerMs);
                return IpRateLimitResult.rejected(Duration.ofMillis(waitMs));
            }
        }

        synchronized boolean isIdle(long idleThresholdMs) {
            return (System.currentTimeMillis() - lastRefillTimestamp) > idleThresholdMs;
        }
    }

    private final long maxPermits;
    private final Duration window;
    private final ConcurrentHashMap<String, IpBucket> ipBuckets = new ConcurrentHashMap<>();

    public IpRateLimiter(long maxPermits, Duration window) {
        if (maxPermits <= 0) throw new IllegalArgumentException("maxPermits must be positive: " + maxPermits);
        this.maxPermits = maxPermits;
        this.window = Objects.requireNonNull(window, "window must not be null");
    }

    public static IpRateLimiter of(long maxPermits, Duration window) {
        return new IpRateLimiter(maxPermits, window);
    }

    public IpRateLimitResult tryAcquire(String clientIp) {
        return tryAcquire(clientIp, 1);
    }

    public IpRateLimitResult tryAcquire(String clientIp, long permits) {
        if (clientIp == null || clientIp.isBlank()) {
            return IpRateLimitResult.rejected(Duration.ofSeconds(1));
        }

        IpBucket bucket = ipBuckets.computeIfAbsent(clientIp.trim(), k -> new IpBucket(maxPermits, window));
        return bucket.tryAcquire(permits, window);
    }

    public int cleanupExpired() {
        long idleThresholdMs = window.toMillis() * 2;
        int removed = 0;
        for (var entry : ipBuckets.entrySet()) {
            if (entry.getValue().isIdle(idleThresholdMs)) {
                if (ipBuckets.remove(entry.getKey(), entry.getValue())) {
                    removed++;
                }
            }
        }
        return removed;
    }

    public int trackedIpCount() {
        return ipBuckets.size();
    }
}
