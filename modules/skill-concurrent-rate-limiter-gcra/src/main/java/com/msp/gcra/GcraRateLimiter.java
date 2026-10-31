package com.msp.gcra;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ultra-high precision, lock-free GCRA (Generic Cell Rate Algorithm) rate limiter.
 * <p>
 * GCRA is an equivalent algorithm to the leaky bucket / token bucket algorithm,
 * tracking a single Theoretical Arrival Time (TAT) instead of multiple timestamps or counters.
 * It uses AtomicLong CAS (Compare-And-Swap) operations for zero-lock, high-concurrency throughput.
 */
public final class GcraRateLimiter {

    private final long emissionIntervalNanos;
    private final long burstToleranceNanos;
    private final AtomicLong tat;

    /**
     * Constructs a GCRA rate limiter.
     *
     * @param emissionInterval the minimum interval between cells (T)
     * @param burstTolerance   allowable burst tolerance / jitter limit (tau / L)
     */
    public GcraRateLimiter(Duration emissionInterval, Duration burstTolerance) {
        Objects.requireNonNull(emissionInterval, "emissionInterval cannot be null");
        Objects.requireNonNull(burstTolerance, "burstTolerance cannot be null");

        long intervalNanos = emissionInterval.toNanos();
        if (intervalNanos <= 0) {
            throw new IllegalArgumentException("emissionInterval must be greater than 0");
        }
        long toleranceNanos = burstTolerance.toNanos();
        if (toleranceNanos < 0) {
            throw new IllegalArgumentException("burstTolerance cannot be negative");
        }

        this.emissionIntervalNanos = intervalNanos;
        this.burstToleranceNanos = toleranceNanos;
        this.tat = new AtomicLong(0L);
    }

    /**
     * Factory method creating a rate limiter given requests-per-second and maximum burst size.
     *
     * @param requestsPerSecond target rate limit (must be positive)
     * @param maxBurst          maximum burst burst capacity in requests (must be >= 1)
     */
    public static GcraRateLimiter fromRate(double requestsPerSecond, int maxBurst) {
        if (requestsPerSecond <= 0) {
            throw new IllegalArgumentException("requestsPerSecond must be positive");
        }
        if (maxBurst < 1) {
            throw new IllegalArgumentException("maxBurst must be at least 1");
        }

        long intervalNanos = (long) (1_000_000_000.0 / requestsPerSecond);
        if (intervalNanos <= 0) {
            intervalNanos = 1;
        }

        long toleranceNanos = (maxBurst - 1) * intervalNanos;
        return new GcraRateLimiter(Duration.ofNanos(intervalNanos), Duration.ofNanos(toleranceNanos));
    }

    /**
     * Attempts to acquire 1 permit.
     *
     * @return {@code true} if request is conforming and permitted, {@code false} if rate limit exceeded
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * Attempts to acquire {@code count} permits atomically.
     *
     * @param count number of permits to acquire (must be > 0)
     * @return {@code true} if permitted, {@code false} otherwise
     */
    public boolean tryAcquire(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }

        long costNanos = (long) count * emissionIntervalNanos;
        long limitNanos = burstToleranceNanos + emissionIntervalNanos;

        while (true) {
            long now = System.nanoTime();
            long currentTat = tat.get();
            long effectiveTat = (currentTat <= now) ? now : currentTat;
            long nextTat = effectiveTat + costNanos;

            if (nextTat - now > limitNanos) {
                return false;
            }

            if (tat.compareAndSet(currentTat, nextTat)) {
                return true;
            }
        }
    }

    /**
     * Returns the duration until the next single permit is allowed.
     * If a permit can be acquired immediately, returns {@link Duration#ZERO}.
     */
    public Duration getWaitTime() {
        long now = System.nanoTime();
        long currentTat = tat.get();
        long excess = (currentTat - now) - burstToleranceNanos;
        if (excess <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofNanos(excess);
    }

    /**
     * Resets the rate limiter state.
     */
    public void reset() {
        tat.set(0L);
    }

    public Duration getEmissionInterval() {
        return Duration.ofNanos(emissionIntervalNanos);
    }

    public Duration getBurstTolerance() {
        return Duration.ofNanos(burstToleranceNanos);
    }

    public long getTheoreticalArrivalTime() {
        return tat.get();
    }
}
