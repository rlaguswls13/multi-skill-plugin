package com.msp.ratelimit;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe Token Bucket Rate Limiter with burst capacity and smooth continuous refill.
 */
public final class TokenBucketRateLimiter implements RateLimiter {

    private final long capacity;
    private final double refillTokensPerNano;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private double availableTokens;
    private long lastRefillTimeNanos;

    public TokenBucketRateLimiter(long capacity, double tokensPerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        if (tokensPerSecond <= 0.0 || Double.isNaN(tokensPerSecond) || Double.isInfinite(tokensPerSecond)) {
            throw new IllegalArgumentException("Tokens per second must be positive: " + tokensPerSecond);
        }
        this.capacity = capacity;
        this.refillTokensPerNano = tokensPerSecond / 1_000_000_000.0;
        this.availableTokens = capacity;
        this.lastRefillTimeNanos = System.nanoTime();
    }

    public TokenBucketRateLimiter(long capacity, long refillTokens, Duration refillPeriod) {
        Objects.requireNonNull(refillPeriod, "refillPeriod cannot be null");
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("Refill tokens must be positive: " + refillTokens);
        }
        if (refillPeriod.isNegative() || refillPeriod.isZero()) {
            throw new IllegalArgumentException("Refill period must be positive: " + refillPeriod);
        }
        this.capacity = capacity;
        long periodNanos = refillPeriod.toNanos();
        this.refillTokensPerNano = (double) refillTokens / (double) periodNanos;
        this.availableTokens = capacity;
        this.lastRefillTimeNanos = System.nanoTime();
    }

    @Override
    public boolean tryAcquire(int permits) {
        validatePermits(permits);
        lock.lock();
        try {
            refill();
            if (availableTokens >= permits) {
                availableTokens -= permits;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean tryAcquire(int permits, Duration timeout) throws InterruptedException {
        validatePermits(permits);
        Objects.requireNonNull(timeout, "timeout cannot be null");

        if (permits > capacity) {
            return false;
        }

        long remainingNanos = timeout.toNanos();
        lock.lockInterruptibly();
        try {
            refill();
            if (availableTokens >= permits) {
                availableTokens -= permits;
                return true;
            }
            if (remainingNanos <= 0) {
                return false;
            }

            long deadline = System.nanoTime() + remainingNanos;
            while (true) {
                refill();
                if (availableTokens >= permits) {
                    availableTokens -= permits;
                    return true;
                }

                double needed = permits - availableTokens;
                long waitNanos = (long) Math.ceil(needed / refillTokensPerNano);
                long timeUntilDeadline = deadline - System.nanoTime();

                if (timeUntilDeadline <= 0 || waitNanos > timeUntilDeadline) {
                    return false;
                }

                long nanosToWait = Math.min(waitNanos, timeUntilDeadline);
                if (nanosToWait > 0) {
                    condition.awaitNanos(nanosToWait);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long availablePermits() {
        lock.lock();
        try {
            refill();
            return (long) availableTokens;
        } finally {
            lock.unlock();
        }
    }

    public long capacity() {
        return capacity;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillTimeNanos;
        if (elapsedNanos > 0) {
            double newTokens = elapsedNanos * refillTokensPerNano;
            availableTokens = Math.min(capacity, availableTokens + newTokens);
            lastRefillTimeNanos = now;
            condition.signalAll();
        }
    }

    private void validatePermits(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("Permits must be positive: " + permits);
        }
    }
}
