package com.msp.ratelimit;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe Sliding Window Counter Rate Limiter.
 * Divides the time window into granular slices for smooth rate limiting without boundary bursts.
 */
public final class SlidingWindowRateLimiter implements RateLimiter {

    private static final int DEFAULT_SLICES = 60;

    private final long maxPermits;
    private final long windowNanos;
    private final int slices;
    private final long sliceDurationNanos;

    private final long[] sliceCounts;
    private final long[] sliceWindowIds;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public SlidingWindowRateLimiter(long maxPermits, Duration window) {
        this(maxPermits, window, DEFAULT_SLICES);
    }

    public SlidingWindowRateLimiter(long maxPermits, Duration window, int slices) {
        Objects.requireNonNull(window, "window cannot be null");
        if (maxPermits <= 0) {
            throw new IllegalArgumentException("maxPermits must be positive: " + maxPermits);
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive: " + window);
        }
        if (slices <= 0) {
            throw new IllegalArgumentException("slices must be positive: " + slices);
        }

        this.maxPermits = maxPermits;
        this.windowNanos = window.toNanos();
        this.slices = slices;
        this.sliceDurationNanos = Math.max(1L, this.windowNanos / slices);
        this.sliceCounts = new long[slices];
        this.sliceWindowIds = new long[slices];
        for (int i = 0; i < slices; i++) {
            this.sliceWindowIds[i] = -1L;
        }
    }

    @Override
    public boolean tryAcquire(int permits) {
        validatePermits(permits);
        if (permits > maxPermits) {
            return false;
        }

        lock.lock();
        try {
            long now = System.nanoTime();
            long currentWindowId = now / sliceDurationNanos;
            int currentIndex = (int) (Math.floorMod(currentWindowId, slices));

            long currentTotal = calculateWindowUsage(currentWindowId);
            if (currentTotal + permits <= maxPermits) {
                recordPermits(currentIndex, currentWindowId, permits);
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

        if (permits > maxPermits) {
            return false;
        }

        long remainingNanos = timeout.toNanos();
        lock.lockInterruptibly();
        try {
            long deadline = System.nanoTime() + remainingNanos;

            while (true) {
                long now = System.nanoTime();
                long currentWindowId = now / sliceDurationNanos;
                int currentIndex = (int) (Math.floorMod(currentWindowId, slices));

                long currentTotal = calculateWindowUsage(currentWindowId);
                if (currentTotal + permits <= maxPermits) {
                    recordPermits(currentIndex, currentWindowId, permits);
                    return true;
                }

                long timeUntilDeadline = deadline - now;
                if (timeUntilDeadline <= 0) {
                    return false;
                }

                // Find how long until the oldest active slice falls out of window
                long waitNanos = calculateTimeToNextExpiry(now, currentWindowId);
                long nanosToWait = Math.min(waitNanos, timeUntilDeadline);
                if (nanosToWait > 0) {
                    condition.awaitNanos(nanosToWait);
                } else {
                    // Fallback small wait
                    condition.awaitNanos(Math.min(1_000_000L, timeUntilDeadline));
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
            long now = System.nanoTime();
            long currentWindowId = now / sliceDurationNanos;
            long used = calculateWindowUsage(currentWindowId);
            return Math.max(0L, maxPermits - used);
        } finally {
            lock.unlock();
        }
    }

    public long maxPermits() {
        return maxPermits;
    }

    public Duration window() {
        return Duration.ofNanos(windowNanos);
    }

    private long calculateWindowUsage(long currentWindowId) {
        long minValidWindowId = currentWindowId - slices + 1;
        long total = 0;
        for (int i = 0; i < slices; i++) {
            if (sliceWindowIds[i] >= minValidWindowId && sliceWindowIds[i] <= currentWindowId) {
                total += sliceCounts[i];
            }
        }
        return total;
    }

    private void recordPermits(int index, long currentWindowId, int permits) {
        if (sliceWindowIds[index] != currentWindowId) {
            sliceWindowIds[index] = currentWindowId;
            sliceCounts[index] = permits;
        } else {
            sliceCounts[index] += permits;
        }
    }

    private long calculateTimeToNextExpiry(long now, long currentWindowId) {
        long minValidWindowId = currentWindowId - slices + 1;
        long oldestId = Long.MAX_VALUE;
        for (int i = 0; i < slices; i++) {
            long id = sliceWindowIds[i];
            if (id >= minValidWindowId && id <= currentWindowId && sliceCounts[i] > 0) {
                if (id < oldestId) {
                    oldestId = id;
                }
            }
        }

        if (oldestId == Long.MAX_VALUE) {
            return sliceDurationNanos;
        }

        // The oldest slice expires when now reaches (oldestId + slices) * sliceDurationNanos
        long expiryTimeNanos = (oldestId + slices) * sliceDurationNanos;
        return Math.max(1_000_000L, expiryTimeNanos - now);
    }

    private void validatePermits(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("Permits must be positive: " + permits);
        }
    }
}
