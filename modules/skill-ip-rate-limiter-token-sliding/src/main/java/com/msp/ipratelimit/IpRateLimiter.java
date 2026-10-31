package com.msp.ipratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * High-performance, thread-safe per-IP sliding token bucket rate limiter
 * with automatic idle IP eviction and zero 3rd-party dependencies.
 */
public final class IpRateLimiter {

    private final long capacity;
    private final double refillTokensPerSecond;
    private final Duration idleTtl;
    private final LongSupplier nanoTimeSupplier;
    private final LongSupplier millisTimeSupplier;

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Constructs a rate limiter with system wall-clock / nanotime suppliers.
     *
     * @param capacity               maximum bucket capacity in tokens
     * @param refillTokensPerSecond  token refill rate per second
     * @param idleTtl                inactivity TTL after which idle IPs can be evicted
     */
    public IpRateLimiter(long capacity, double refillTokensPerSecond, Duration idleTtl) {
        this(capacity, refillTokensPerSecond, idleTtl, System::nanoTime, System::currentTimeMillis);
    }

    /**
     * Package-private constructor for precise, deterministic unit testing with simulated clocks.
     */
    IpRateLimiter(long capacity,
                  double refillTokensPerSecond,
                  Duration idleTtl,
                  LongSupplier nanoTimeSupplier,
                  LongSupplier millisTimeSupplier) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        if (refillTokensPerSecond <= 0.0) {
            throw new IllegalArgumentException("Refill rate must be positive: " + refillTokensPerSecond);
        }
        Objects.requireNonNull(idleTtl, "idleTtl must not be null");
        if (idleTtl.isNegative() || idleTtl.isZero()) {
            throw new IllegalArgumentException("idleTtl must be strictly positive");
        }
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
        this.idleTtl = idleTtl;
        this.nanoTimeSupplier = Objects.requireNonNull(nanoTimeSupplier, "nanoTimeSupplier must not be null");
        this.millisTimeSupplier = Objects.requireNonNull(millisTimeSupplier, "millisTimeSupplier must not be null");
    }

    /**
     * Tries to acquire 1 token for the specified IP address.
     *
     * @param ipAddress IP address
     * @return true if acquired successfully, false if rate limited
     */
    public boolean tryAcquire(String ipAddress) {
        return tryAcquire(ipAddress, 1);
    }

    /**
     * Tries to acquire a specific number of tokens for the specified IP address.
     *
     * @param ipAddress IP address
     * @param tokens    number of tokens to acquire (must be positive)
     * @return true if acquired successfully, false if rate limited
     */
    public boolean tryAcquire(String ipAddress, long tokens) {
        validateIp(ipAddress);
        if (tokens <= 0) {
            throw new IllegalArgumentException("Tokens to acquire must be positive: " + tokens);
        }

        long nowNanos = nanoTimeSupplier.getAsLong();
        long nowMillis = millisTimeSupplier.getAsLong();

        TokenBucket bucket = buckets.computeIfAbsent(
                ipAddress,
                ip -> new TokenBucket(capacity, refillTokensPerSecond, nowNanos, nowMillis)
        );

        return bucket.tryAcquire(tokens, nowNanos, nowMillis);
    }

    /**
     * Returns the currently available tokens for the given IP address.
     * If the IP is not yet tracked, returns the full bucket capacity.
     */
    public long getAvailableTokens(String ipAddress) {
        validateIp(ipAddress);
        TokenBucket bucket = buckets.get(ipAddress);
        if (bucket == null) {
            return capacity;
        }
        return bucket.getAvailableTokens(nanoTimeSupplier.getAsLong());
    }

    /**
     * Removes inactive IP entries whose last access time exceeds the configured {@code idleTtl}.
     *
     * @return number of evicted IP entries
     */
    public int cleanupIdleIps() {
        long nowMillis = millisTimeSupplier.getAsLong();
        long idleTtlMillis = idleTtl.toMillis();
        int evictedCount = 0;

        for (Map.Entry<String, TokenBucket> entry : buckets.entrySet()) {
            if (entry.getValue().isIdle(nowMillis, idleTtlMillis)) {
                // Atomic removal if value unchanged
                if (buckets.remove(entry.getKey(), entry.getValue())) {
                    evictedCount++;
                }
            }
        }
        return evictedCount;
    }

    /**
     * Returns the number of currently tracked IP addresses.
     */
    public int trackedIpCount() {
        return buckets.size();
    }

    /**
     * Resets the token bucket of a specific IP address to its full capacity.
     */
    public void reset(String ipAddress) {
        validateIp(ipAddress);
        TokenBucket bucket = buckets.get(ipAddress);
        if (bucket != null) {
            bucket.reset(nanoTimeSupplier.getAsLong(), millisTimeSupplier.getAsLong());
        }
    }

    /**
     * Clears all tracked IP address buckets.
     */
    public void clear() {
        buckets.clear();
    }

    public long capacity() {
        return capacity;
    }

    public double refillTokensPerSecond() {
        return refillTokensPerSecond;
    }

    public Duration idleTtl() {
        return idleTtl;
    }

    private static void validateIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("IP address must not be null or blank");
        }
    }

    /**
     * Internal thread-safe token bucket per IP address.
     */
    private static final class TokenBucket {
        private final long capacity;
        private final double refillRatePerSec;
        private double availableTokens;
        private long lastRefillNanos;
        private volatile long lastAccessMillis;
        private final Object lock = new Object();

        TokenBucket(long capacity, double refillRatePerSec, long nowNanos, long nowMillis) {
            this.capacity = capacity;
            this.refillRatePerSec = refillRatePerSec;
            this.availableTokens = capacity;
            this.lastRefillNanos = nowNanos;
            this.lastAccessMillis = nowMillis;
        }

        boolean tryAcquire(long tokens, long nowNanos, long nowMillis) {
            synchronized (lock) {
                refill(nowNanos);
                lastAccessMillis = nowMillis;
                if (availableTokens >= tokens) {
                    availableTokens -= tokens;
                    return true;
                }
                return false;
            }
        }

        long getAvailableTokens(long nowNanos) {
            synchronized (lock) {
                refill(nowNanos);
                return (long) availableTokens;
            }
        }

        private void refill(long nowNanos) {
            if (nowNanos > lastRefillNanos) {
                long elapsedNanos = nowNanos - lastRefillNanos;
                double elapsedSec = elapsedNanos / 1_000_000_000.0;
                double added = elapsedSec * refillRatePerSec;
                if (added > 0.0) {
                    availableTokens = Math.min((double) capacity, availableTokens + added);
                    lastRefillNanos = nowNanos;
                }
            }
        }

        boolean isIdle(long nowMillis, long idleTtlMillis) {
            return (nowMillis - lastAccessMillis) > idleTtlMillis;
        }

        void reset(long nowNanos, long nowMillis) {
            synchronized (lock) {
                this.availableTokens = capacity;
                this.lastRefillNanos = nowNanos;
                this.lastAccessMillis = nowMillis;
            }
        }
    }
}
