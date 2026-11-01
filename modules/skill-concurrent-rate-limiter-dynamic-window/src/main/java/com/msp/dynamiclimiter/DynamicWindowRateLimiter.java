package com.msp.dynamiclimiter;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Self-adaptive Rate Limiter dynamically tuning window capacity via
 * Additive Increase / Multiplicative Decrease (AIMD) based on downstream latency
 * or failure feedback.
 *
 * <p>Uses lock-free atomic concurrency for ultra-high throughput.</p>
 */
public final class DynamicWindowRateLimiter {

    public static final long DEFAULT_ADDITIVE_STEP = 1L;
    public static final double DEFAULT_DECREASE_FACTOR = 0.5;

    private final long minPermits;
    private final long maxPermits;
    private final long initialPermits;
    private final long windowDurationNanos;
    private final long additiveIncreaseStep;
    private final double multiplicativeDecreaseFactor;

    private final AtomicLong currentPermits;
    private final AtomicLong windowStartNanos;
    private final AtomicLong usedInWindow;

    /**
     * Creates a new DynamicWindowRateLimiter with default AIMD parameters (+1 step, *0.5 backoff).
     *
     * @param minPermits     minimum allowed permits per window (>= 1)
     * @param maxPermits     maximum allowed permits per window (>= minPermits)
     * @param initialPermits starting permits (between minPermits and maxPermits)
     * @param windowDuration duration of each rate limit window
     */
    public DynamicWindowRateLimiter(long minPermits, long maxPermits, long initialPermits, Duration windowDuration) {
        this(minPermits, maxPermits, initialPermits, windowDuration, DEFAULT_ADDITIVE_STEP, DEFAULT_DECREASE_FACTOR);
    }

    /**
     * Creates a new DynamicWindowRateLimiter with custom AIMD parameters.
     *
     * @param minPermits                 minimum allowed permits per window (>= 1)
     * @param maxPermits                 maximum allowed permits per window (>= minPermits)
     * @param initialPermits             starting permits (between minPermits and maxPermits)
     * @param windowDuration             duration of each rate limit window
     * @param additiveIncreaseStep       permits added upon successful feedback (>= 1)
     * @param multiplicativeDecreaseFactor backoff multiplier on failure or latency spike (0.0 < factor < 1.0)
     */
    public DynamicWindowRateLimiter(
            long minPermits,
            long maxPermits,
            long initialPermits,
            Duration windowDuration,
            long additiveIncreaseStep,
            double multiplicativeDecreaseFactor) {

        if (minPermits < 1) {
            throw new IllegalArgumentException("minPermits must be >= 1: " + minPermits);
        }
        if (maxPermits < minPermits) {
            throw new IllegalArgumentException("maxPermits (" + maxPermits + ") must be >= minPermits (" + minPermits + ")");
        }
        if (initialPermits < minPermits || initialPermits > maxPermits) {
            throw new IllegalArgumentException("initialPermits (" + initialPermits + ") must be between minPermits and maxPermits");
        }
        Objects.requireNonNull(windowDuration, "windowDuration must not be null");
        if (windowDuration.isZero() || windowDuration.isNegative()) {
            throw new IllegalArgumentException("windowDuration must be strictly positive");
        }
        if (additiveIncreaseStep < 1) {
            throw new IllegalArgumentException("additiveIncreaseStep must be >= 1");
        }
        if (multiplicativeDecreaseFactor <= 0.0 || multiplicativeDecreaseFactor >= 1.0) {
            throw new IllegalArgumentException("multiplicativeDecreaseFactor must be between 0.0 and 1.0 (exclusive)");
        }

        this.minPermits = minPermits;
        this.maxPermits = maxPermits;
        this.initialPermits = initialPermits;
        this.windowDurationNanos = windowDuration.toNanos();
        this.additiveIncreaseStep = additiveIncreaseStep;
        this.multiplicativeDecreaseFactor = multiplicativeDecreaseFactor;

        this.currentPermits = new AtomicLong(initialPermits);
        this.windowStartNanos = new AtomicLong(System.nanoTime());
        this.usedInWindow = new AtomicLong(0);
    }

    /**
     * Attempts to acquire 1 permit within the current window.
     *
     * @return true if acquired, false if rate limit exceeded
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * Attempts to acquire the specified number of permits within the current window.
     *
     * @param permits number of permits to acquire (>= 1)
     * @return true if acquired, false if rate limit exceeded
     */
    public boolean tryAcquire(long permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive: " + permits);
        }

        while (true) {
            long now = System.nanoTime();
            long start = windowStartNanos.get();
            long elapsed = now - start;

            if (elapsed >= windowDurationNanos) {
                // Window rollover
                if (windowStartNanos.compareAndSet(start, now)) {
                    usedInWindow.set(0);
                }
                continue;
            }

            long limit = currentPermits.get();
            long currentUsed = usedInWindow.get();

            if (currentUsed + permits > limit) {
                return false;
            }

            if (usedInWindow.compareAndSet(currentUsed, currentUsed + permits)) {
                return true;
            }
        }
    }

    /**
     * Records feedback from downstream request execution to adjust window capacity via AIMD.
     *
     * @param success       true if request succeeded, false on error/timeout
     * @param latency       observed request latency
     * @param targetLatency target SLO latency threshold
     */
    public void recordFeedback(boolean success, Duration latency, Duration targetLatency) {
        Objects.requireNonNull(latency, "latency must not be null");
        Objects.requireNonNull(targetLatency, "targetLatency must not be null");

        currentPermits.updateAndGet(current -> {
            if (success && latency.compareTo(targetLatency) <= 0) {
                // Additive Increase (AIMD)
                return Math.min(maxPermits, current + additiveIncreaseStep);
            } else {
                // Multiplicative Decrease (AIMD)
                long decreased = (long) Math.floor(current * multiplicativeDecreaseFactor);
                return Math.max(minPermits, decreased);
            }
        });
    }

    /**
     * Returns the current dynamically tuned permits limit.
     *
     * @return current permits capacity
     */
    public long getCurrentPermits() {
        return currentPermits.get();
    }

    /**
     * Updates the current permit capacity directly, clamping within [minPermits, maxPermits].
     *
     * @param newPermits proposed new permit limit
     */
    public void updatePermitsDirectly(long newPermits) {
        long clamped = Math.clamp(newPermits, minPermits, maxPermits);
        currentPermits.set(clamped);
    }

    /**
     * Returns the remaining available permits in the current window.
     *
     * @return available permits (>= 0)
     */
    public int getAvailablePermitsInWindow() {
        long now = System.nanoTime();
        long start = windowStartNanos.get();
        long elapsed = now - start;
        long used = (elapsed >= windowDurationNanos) ? 0 : usedInWindow.get();
        long available = Math.max(0, currentPermits.get() - used);
        return (int) Math.min(Integer.MAX_VALUE, available);
    }

    /**
     * Resets the limiter to initial configuration and clears window usage.
     */
    public void reset() {
        currentPermits.set(initialPermits);
        windowStartNanos.set(System.nanoTime());
        usedInWindow.set(0);
    }

    public long getMinPermits() {
        return minPermits;
    }

    public long getMaxPermits() {
        return maxPermits;
    }

    public long getInitialPermits() {
        return initialPermits;
    }

    public Duration getWindowDuration() {
        return Duration.ofNanos(windowDurationNanos);
    }
}
