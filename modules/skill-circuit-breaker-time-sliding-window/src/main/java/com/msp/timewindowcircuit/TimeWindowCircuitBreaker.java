package com.msp.timewindowcircuit;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe Circuit Breaker based on a circular sliding time window.
 * <p>
 * Divides the sliding window into N granular time buckets to track call success/failure rates
 * with minimal memory overhead and strict O(1) time complexity.
 */
public final class TimeWindowCircuitBreaker {

    public static final int DEFAULT_MIN_CALLS = 5;
    public static final int DEFAULT_BUCKETS = 10;

    private final long windowDurationMillis;
    private final int failureRateThreshold;
    private final long openDurationMillis;
    private final int minCallCount;
    private final int bucketCount;
    private final long bucketDurationMillis;
    private final Clock clock;

    private final ReentrantLock lock = new ReentrantLock();
    private final Bucket[] buckets;

    private CircuitState state = CircuitState.CLOSED;
    private long openTimestampMillis = 0;
    private int halfOpenPermitsAvailable = 1;

    /**
     * Creates a circuit breaker with default minimum calls (5) and bucket count (10).
     *
     * @param windowDuration       duration of the sliding monitoring window
     * @param failureRateThreshold failure rate percentage threshold (1 to 100)
     * @param openDuration         duration the breaker stays OPEN before attempting HALF_OPEN trial
     */
    public TimeWindowCircuitBreaker(Duration windowDuration, int failureRateThreshold, Duration openDuration) {
        this(windowDuration, failureRateThreshold, openDuration, DEFAULT_MIN_CALLS, DEFAULT_BUCKETS, Clock.systemUTC());
    }

    /**
     * Creates a circuit breaker with custom bucket count and minimum call threshold.
     */
    public TimeWindowCircuitBreaker(Duration windowDuration, int failureRateThreshold, Duration openDuration,
                                    int minCallCount, int bucketCount) {
        this(windowDuration, failureRateThreshold, openDuration, minCallCount, bucketCount, Clock.systemUTC());
    }

    /**
     * Full constructor with clock injection for deterministic testing.
     */
    public TimeWindowCircuitBreaker(Duration windowDuration, int failureRateThreshold, Duration openDuration,
                                    int minCallCount, int bucketCount, Clock clock) {
        Objects.requireNonNull(windowDuration, "windowDuration must not be null");
        Objects.requireNonNull(openDuration, "openDuration must not be null");
        Objects.requireNonNull(clock, "clock must not be null");

        if (windowDuration.toMillis() <= 0) {
            throw new IllegalArgumentException("windowDuration must be positive");
        }
        if (openDuration.toMillis() <= 0) {
            throw new IllegalArgumentException("openDuration must be positive");
        }
        if (failureRateThreshold < 1 || failureRateThreshold > 100) {
            throw new IllegalArgumentException("failureRateThreshold must be between 1 and 100: " + failureRateThreshold);
        }
        if (minCallCount < 1) {
            throw new IllegalArgumentException("minCallCount must be >= 1: " + minCallCount);
        }
        if (bucketCount < 2) {
            throw new IllegalArgumentException("bucketCount must be >= 2: " + bucketCount);
        }

        this.windowDurationMillis = windowDuration.toMillis();
        this.failureRateThreshold = failureRateThreshold;
        this.openDurationMillis = openDuration.toMillis();
        this.minCallCount = minCallCount;
        this.bucketCount = bucketCount;
        this.bucketDurationMillis = Math.max(1, windowDurationMillis / bucketCount);
        this.clock = clock;

        this.buckets = new Bucket[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            this.buckets[i] = new Bucket();
        }
    }

    /**
     * Attempts to acquire execution permission through the circuit breaker.
     *
     * @return true if permission is granted; false if rejected
     */
    public boolean tryAcquirePermission() {
        lock.lock();
        try {
            long now = clock.millis();
            updateStateIfTimeElapsed(now);

            return switch (state) {
                case CLOSED -> true;
                case OPEN -> false;
                case HALF_OPEN -> {
                    if (halfOpenPermitsAvailable > 0) {
                        halfOpenPermitsAvailable--;
                        yield true;
                    }
                    yield false;
                }
            };
        } finally {
            lock.unlock();
        }
    }

    /**
     * Records a successful execution.
     */
    public void recordSuccess() {
        lock.lock();
        try {
            long now = clock.millis();
            updateStateIfTimeElapsed(now);

            if (state == CircuitState.HALF_OPEN) {
                // Trial succeeded, transition back to CLOSED
                transitionToClosed(now);
                return;
            }

            if (state == CircuitState.CLOSED) {
                getOrCreateBucket(now).successCount++;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Records a failed execution.
     */
    public void recordFailure() {
        lock.lock();
        try {
            long now = clock.millis();
            updateStateIfTimeElapsed(now);

            if (state == CircuitState.HALF_OPEN) {
                // Trial failed, immediately trip back to OPEN
                transitionToOpen(now);
                return;
            }

            if (state == CircuitState.CLOSED) {
                getOrCreateBucket(now).failureCount++;
                checkAndEvaluateThreshold(now);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current state of the circuit breaker.
     */
    public CircuitState getState() {
        lock.lock();
        try {
            updateStateIfTimeElapsed(clock.millis());
            return state;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current failure rate percentage (0.0 to 100.0) in the active sliding window.
     */
    public double getFailureRate() {
        lock.lock();
        try {
            long now = clock.millis();
            CallStats stats = getValidWindowStats(now);
            if (stats.total() == 0) {
                return 0.0;
            }
            return ((double) stats.failures() / stats.total()) * 100.0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Manually resets the circuit breaker to CLOSED state and clears all statistics.
     */
    public void reset() {
        lock.lock();
        try {
            transitionToClosed(clock.millis());
        } finally {
            lock.unlock();
        }
    }

    private void updateStateIfTimeElapsed(long now) {
        if (state == CircuitState.OPEN) {
            if (now - openTimestampMillis >= openDurationMillis) {
                state = CircuitState.HALF_OPEN;
                halfOpenPermitsAvailable = 1;
            }
        }
    }

    private void checkAndEvaluateThreshold(long now) {
        CallStats stats = getValidWindowStats(now);
        if (stats.total() >= minCallCount) {
            double rate = ((double) stats.failures() / stats.total()) * 100.0;
            if (rate >= failureRateThreshold) {
                transitionToOpen(now);
            }
        }
    }

    private void transitionToOpen(long now) {
        state = CircuitState.OPEN;
        openTimestampMillis = now;
        clearBuckets();
    }

    private void transitionToClosed(long now) {
        state = CircuitState.CLOSED;
        openTimestampMillis = 0;
        halfOpenPermitsAvailable = 1;
        clearBuckets();
    }

    private void clearBuckets() {
        for (Bucket b : buckets) {
            b.reset();
        }
    }

    private Bucket getOrCreateBucket(long now) {
        long bucketIndex = (now / bucketDurationMillis) % bucketCount;
        Bucket b = buckets[(int) bucketIndex];
        long expectedStartTime = (now / bucketDurationMillis) * bucketDurationMillis;

        if (b.startTimeMillis != expectedStartTime) {
            b.startTimeMillis = expectedStartTime;
            b.successCount = 0;
            b.failureCount = 0;
        }
        return b;
    }

    private CallStats getValidWindowStats(long now) {
        long earliestValidTime = now - windowDurationMillis;
        long totalSuccess = 0;
        long totalFailure = 0;

        for (Bucket b : buckets) {
            if (b.startTimeMillis >= earliestValidTime && b.startTimeMillis <= now) {
                totalSuccess += b.successCount;
                totalFailure += b.failureCount;
            }
        }
        return new CallStats(totalSuccess, totalFailure);
    }

    private static final class Bucket {
        long startTimeMillis = -1;
        long successCount = 0;
        long failureCount = 0;

        void reset() {
            startTimeMillis = -1;
            successCount = 0;
            failureCount = 0;
        }
    }

    private record CallStats(long successes, long failures) {
        long total() {
            return successes + failures;
        }
    }
}
