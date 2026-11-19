package com.msp.redisscript;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, pure Java in-memory emulator that replicates the exact behavior
 * of the distributed Redis Lua sliding-window rate limiter.
 */
public final class RedisSlidingWindowRateLimiterEmulator {

    private record Entry(long timestamp, String memberId) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry o) {
            int cmp = Long.compare(this.timestamp, o.timestamp);
            if (cmp != 0) return cmp;
            return this.memberId.compareTo(o.memberId);
        }
    }

    private static final class KeyBucket {
        private final TreeSet<Entry> entries = new TreeSet<>();
        private long lastAccessTime = System.currentTimeMillis();

        synchronized RateLimitDecision tryAcquire(long nowMs, long windowMs, long limit, String memberId) {
            this.lastAccessTime = nowMs;
            long clearBefore = nowMs - windowMs;

            // 1. ZREMRANGEBYSCORE key -inf clear_before
            entries.removeIf(e -> e.timestamp <= clearBefore);

            // 2. ZCARD key
            int currentRequests = entries.size();

            // 3. Check limit
            if (currentRequests < limit) {
                // ZADD key now member
                entries.add(new Entry(nowMs, memberId));
                long remaining = limit - currentRequests - 1;
                return RateLimitDecision.allow(remaining, nowMs + windowMs);
            } else {
                // ZRANGE key 0 0 WITHSCORES
                Entry oldest = entries.first();
                long resetTime = oldest.timestamp + windowMs;
                long retryAfter = Math.max(0, resetTime - nowMs);
                return RateLimitDecision.reject(retryAfter, resetTime);
            }
        }

        synchronized RateLimitDecision check(long nowMs, long windowMs, long limit) {
            long clearBefore = nowMs - windowMs;
            long activeCount = entries.stream().filter(e -> e.timestamp > clearBefore).count();
            if (activeCount < limit) {
                long remaining = limit - activeCount;
                return RateLimitDecision.allow(remaining, nowMs + windowMs);
            } else {
                Entry oldest = entries.stream().filter(e -> e.timestamp > clearBefore).findFirst().orElse(null);
                long resetTime = oldest != null ? oldest.timestamp + windowMs : nowMs + windowMs;
                long retryAfter = Math.max(0, resetTime - nowMs);
                return RateLimitDecision.reject(retryAfter, resetTime);
            }
        }

        synchronized int countActive(long nowMs, long windowMs) {
            long clearBefore = nowMs - windowMs;
            entries.removeIf(e -> e.timestamp <= clearBefore);
            return entries.size();
        }
    }

    private final ConcurrentHashMap<String, KeyBucket> storage = new ConcurrentHashMap<>();
    private final AtomicLong memberSequence = new AtomicLong();

    /**
     * Attempts to acquire a rate limit token using the system clock.
     */
    public RateLimitDecision tryAcquire(String key, int limit, Duration window) {
        return tryAcquire(key, limit, window, Instant.now(), null);
    }

    /**
     * Attempts to acquire a rate limit token with an explicit timestamp and member id.
     */
    public RateLimitDecision tryAcquire(String key, int limit, Duration window, Instant now, String memberId) {
        Objects.requireNonNull(key, "Rate limit key cannot be null");
        Objects.requireNonNull(window, "Window duration cannot be null");
        Objects.requireNonNull(now, "Timestamp cannot be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive: " + limit);
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Window duration must be positive: " + window);
        }

        long nowMs = now.toEpochMilli();
        long windowMs = window.toMillis();
        String member = memberId != null ? memberId : (nowMs + "-" + memberSequence.incrementAndGet());

        KeyBucket bucket = storage.computeIfAbsent(key, k -> new KeyBucket());
        return bucket.tryAcquire(nowMs, windowMs, limit, member);
    }

    /**
     * Inspects rate limit status without recording a request.
     */
    public RateLimitDecision check(String key, int limit, Duration window, Instant now) {
        Objects.requireNonNull(key, "Rate limit key cannot be null");
        Objects.requireNonNull(window, "Window duration cannot be null");
        Objects.requireNonNull(now, "Timestamp cannot be null");

        KeyBucket bucket = storage.get(key);
        if (bucket == null) {
            return RateLimitDecision.allow(limit, now.toEpochMilli() + window.toMillis());
        }
        return bucket.check(now.toEpochMilli(), window.toMillis(), limit);
    }

    /**
     * Resets / clears all rate limit entries for a specific key.
     */
    public void reset(String key) {
        if (key != null) {
            storage.remove(key);
        }
    }

    /**
     * Clears all keys from the rate limiter.
     */
    public void clear() {
        storage.clear();
    }

    /**
     * Returns the number of distinct keys currently tracked.
     */
    public int getTrackedKeyCount() {
        return storage.size();
    }

    /**
     * Returns current number of active requests in the window for a given key.
     */
    public int getActiveCount(String key, Duration window, Instant now) {
        KeyBucket bucket = storage.get(key);
        if (bucket == null) return 0;
        return bucket.countActive(now.toEpochMilli(), window.toMillis());
    }
}
