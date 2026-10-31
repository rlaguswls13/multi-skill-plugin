package com.msp.leakybucket;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe, nanosecond-precision Leaky Bucket rate limiter and traffic smoother in pure Java 21.
 * Controls traffic bursts by holding tokens up to {@code capacity} and releasing (leaking) them
 * at a smooth, constant rate of {@code leakRatePerSecond}.
 */
public final class LeakyBucketRateLimiter {

    private final long capacity;
    private final double leakRatePerSecond;
    private final ReentrantLock lock = new ReentrantLock();

    private double waterLevel;
    private long lastLeakTimeNanos;

    /**
     * Constructs a LeakyBucketRateLimiter.
     *
     * @param capacity          maximum burst capacity (water drops) that the bucket can hold (must be > 0)
     * @param leakRatePerSecond constant rate at which drops leak from the bucket per second (must be > 0)
     */
    public LeakyBucketRateLimiter(long capacity, double leakRatePerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        if (leakRatePerSecond <= 0.0 || Double.isNaN(leakRatePerSecond) || Double.isInfinite(leakRatePerSecond)) {
            throw new IllegalArgumentException("leakRatePerSecond must be positive and finite: " + leakRatePerSecond);
        }
        this.capacity = capacity;
        this.leakRatePerSecond = leakRatePerSecond;
        this.waterLevel = 0.0;
        this.lastLeakTimeNanos = System.nanoTime();
    }

    /**
     * Attempts to acquire 1 drop without blocking.
     *
     * @return true if acquired successfully, false if bucket is full
     */
    public boolean tryAcquire() {
        return tryAcquire(1L);
    }

    /**
     * Attempts to acquire the specified number of drops without blocking.
     *
     * @param drops number of drops to acquire (must be &gt;= 0)
     * @return true if acquired successfully, false if bucket cannot accommodate the drops
     */
    public boolean tryAcquire(long drops) {
        if (drops < 0) {
            throw new IllegalArgumentException("drops cannot be negative: " + drops);
        }
        if (drops == 0) {
            return true;
        }

        lock.lock();
        try {
            leak();
            if (waterLevel + drops <= capacity) {
                waterLevel += drops;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Acquires 1 drop, blocking if necessary until the bucket leaks enough capacity.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void acquire() throws InterruptedException {
        acquire(1L);
    }

    /**
     * Acquires the specified number of drops, blocking if necessary until the bucket leaks enough capacity.
     *
     * @param drops number of drops to acquire (must be &gt; 0 and &lt;= capacity)
     * @throws InterruptedException if interrupted while waiting
     */
    public void acquire(long drops) throws InterruptedException {
        if (drops <= 0) {
            throw new IllegalArgumentException("drops must be positive: " + drops);
        }
        if (drops > capacity) {
            throw new IllegalArgumentException("drops (" + drops + ") exceeds bucket capacity (" + capacity + ")");
        }

        while (true) {
            long waitNanos = 0L;
            lock.lock();
            try {
                leak();
                if (waterLevel + drops <= capacity) {
                    waterLevel += drops;
                    return;
                }
                double excess = (waterLevel + drops) - capacity;
                waitNanos = (long) Math.ceil((excess / leakRatePerSecond) * 1_000_000_000.0);
            } finally {
                lock.unlock();
            }

            if (waitNanos > 0) {
                long millis = waitNanos / 1_000_000L;
                int nanos = (int) (waitNanos % 1_000_000L);
                Thread.sleep(millis, nanos);
            } else {
                Thread.sleep(1);
            }
        }
    }

    /**
     * Returns the current water level in the bucket (after calculating elapsed leak).
     *
     * @return current water drop count
     */
    public long currentLevel() {
        lock.lock();
        try {
            leak();
            return (long) waterLevel;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the maximum capacity of the bucket.
     *
     * @return bucket capacity
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Returns the leak rate per second.
     *
     * @return leak rate
     */
    public double getLeakRatePerSecond() {
        return leakRatePerSecond;
    }

    /**
     * Resets the bucket water level to empty.
     */
    public void reset() {
        lock.lock();
        try {
            this.waterLevel = 0.0;
            this.lastLeakTimeNanos = System.nanoTime();
        } finally {
            lock.unlock();
        }
    }

    private void leak() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastLeakTimeNanos;
        if (elapsedNanos > 0) {
            double leakedDrops = (elapsedNanos / 1_000_000_000.0) * leakRatePerSecond;
            waterLevel = Math.max(0.0, waterLevel - leakedDrops);
            lastLeakTimeNanos = now;
        }
    }
}
