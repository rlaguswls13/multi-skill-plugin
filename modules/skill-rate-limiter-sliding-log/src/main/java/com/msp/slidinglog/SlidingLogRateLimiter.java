package com.msp.slidinglog;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-precision, microsecond-accurate rate limiter implementing the Sliding Log algorithm.
 * <p>
 * Unlike Fixed Window or Sliding Window Counter approximations, Sliding Log tracks exact request
 * timestamps in a per-key sliding deque, eliminating burst-spike edge effects at window boundaries.
 * <p>
 * Thread-safe using {@link ConcurrentHashMap} with fine-grained per-key locks and automatic
 * stale log entry eviction.
 */
public final class SlidingLogRateLimiter {

    private static final String DEFAULT_KEY = "__GLOBAL__";
    private static final long DEFAULT_STALE_EVICTION_INTERVAL_MICROS = 10_000_000L; // 10 seconds
    private static final int DEFAULT_EVICTION_CALL_THRESHOLD = 512;

    /**
     * Microsecond time provider interface for testability and custom clocks.
     */
    @FunctionalInterface
    public interface TimeSource {
        long getMicros();
    }

    private final ConcurrentHashMap<String, KeyLog> logMap = new ConcurrentHashMap<>();
    private final TimeSource timeSource;
    private final AtomicLong callCounter = new AtomicLong();
    private final AtomicLong lastEvictionMicros = new AtomicLong();

    /**
     * Creates a SlidingLogRateLimiter using monotonic {@link System#nanoTime()} / 1000.
     */
    public SlidingLogRateLimiter() {
        this(() -> System.nanoTime() / 1_000L);
    }

    /**
     * Creates a SlidingLogRateLimiter with a custom {@link TimeSource}.
     */
    public SlidingLogRateLimiter(TimeSource timeSource) {
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource must not be null");
        this.lastEvictionMicros.set(timeSource.getMicros());
    }

    /**
     * Attempts to acquire 1 permit under the global rate limit.
     *
     * @param maxRequests maximum allowed requests within window
     * @param window      duration window
     * @return true if permit was granted, false if rate limit exceeded
     */
    public boolean tryAcquire(int maxRequests, Duration window) {
        return tryAcquire(DEFAULT_KEY, maxRequests, window);
    }

    /**
     * Attempts to acquire 1 permit for the specified key (e.g. IP, tenant, user ID).
     *
     * @param key         rate limit partition key
     * @param maxRequests maximum allowed requests within window
     * @param window      duration window
     * @return true if permit was granted, false if rate limit exceeded
     */
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        Objects.requireNonNull(key, "key must not be null");
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be greater than 0");
        }
        Objects.requireNonNull(window, "window must not be null");
        long windowMicros = window.toNanos() / 1_000L;
        if (windowMicros <= 0) {
            throw new IllegalArgumentException("window must be positive");
        }

        maybeTriggerStaleEviction(windowMicros);

        long nowMicros = timeSource.getMicros();
        KeyLog keyLog = logMap.computeIfAbsent(key, k -> new KeyLog());
        return keyLog.tryAcquire(nowMicros, maxRequests, windowMicros);
    }

    /**
     * Returns remaining available permits for the global rate limit.
     */
    public int getRemainingPermits(int maxRequests, Duration window) {
        return getRemainingPermits(DEFAULT_KEY, maxRequests, window);
    }

    /**
     * Returns remaining available permits for the specified key.
     */
    public int getRemainingPermits(String key, int maxRequests, Duration window) {
        Objects.requireNonNull(key, "key must not be null");
        long windowMicros = window.toNanos() / 1_000L;
        long nowMicros = timeSource.getMicros();
        KeyLog keyLog = logMap.get(key);
        if (keyLog == null) {
            return maxRequests;
        }
        return Math.max(0, maxRequests - keyLog.countInWindow(nowMicros, windowMicros));
    }

    /**
     * Resets rate limit records for a specific key.
     */
    public void reset(String key) {
        Objects.requireNonNull(key, "key must not be null");
        logMap.remove(key);
    }

    /**
     * Resets all rate limit logs across all keys.
     */
    public void resetAll() {
        logMap.clear();
    }

    /**
     * Returns current number of partition keys actively tracked.
     */
    public int getActiveKeyCount() {
        return logMap.size();
    }

    /**
     * Evicts stale key queues that have had no activity for longer than maxIdle duration.
     *
     * @param maxIdle maximum inactive duration before key log is evicted
     * @return number of evicted keys
     */
    public int evictStale(Duration maxIdle) {
        Objects.requireNonNull(maxIdle, "maxIdle must not be null");
        long maxIdleMicros = maxIdle.toNanos() / 1_000L;
        long nowMicros = timeSource.getMicros();
        return (int) logMap.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(DEFAULT_KEY) && entry.getValue().isStale(nowMicros, maxIdleMicros))
                .peek(entry -> logMap.remove(entry.getKey(), entry.getValue()))
                .count();
    }

    private void maybeTriggerStaleEviction(long windowMicros) {
        long calls = callCounter.incrementAndGet();
        if (calls % DEFAULT_EVICTION_CALL_THRESHOLD == 0) {
            long nowMicros = timeSource.getMicros();
            long last = lastEvictionMicros.get();
            long interval = Math.max(DEFAULT_STALE_EVICTION_INTERVAL_MICROS, windowMicros * 2);
            if (nowMicros - last >= interval && lastEvictionMicros.compareAndSet(last, nowMicros)) {
                // Auto evict entries inactive for 2x the window
                evictStale(Duration.ofNanos(interval * 1_000L));
            }
        }
    }

    /**
     * Synchronized per-key sliding timestamp deque.
     */
    private static final class KeyLog {
        private final ArrayDeque<Long> log = new ArrayDeque<>();
        private long lastAccessMicros;

        synchronized boolean tryAcquire(long nowMicros, int maxRequests, long windowMicros) {
            lastAccessMicros = nowMicros;
            long cutoff = nowMicros - windowMicros;

            // Remove expired timestamps outside the sliding window
            while (!log.isEmpty() && log.peekFirst() <= cutoff) {
                log.pollFirst();
            }

            if (log.size() < maxRequests) {
                log.addLast(nowMicros);
                return true;
            }
            return false;
        }

        synchronized int countInWindow(long nowMicros, long windowMicros) {
            long cutoff = nowMicros - windowMicros;
            while (!log.isEmpty() && log.peekFirst() <= cutoff) {
                log.pollFirst();
            }
            return log.size();
        }

        synchronized boolean isStale(long nowMicros, long maxIdleMicros) {
            return (nowMicros - lastAccessMicros) > maxIdleMicros;
        }
    }
}
