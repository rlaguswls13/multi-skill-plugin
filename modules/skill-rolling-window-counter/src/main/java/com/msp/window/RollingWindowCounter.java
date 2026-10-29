package com.msp.window;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/**
 * High-throughput sliding time-window counter with sub-bucket granularity and spike detection.
 * <p>
 * Implemented using a lock-free, cache-line-striped circular ring buffer.
 * Provides microsecond-level rate calculations, smooth window eviction with zero background threads,
 * and statistical spike detection.
 */
public class RollingWindowCounter {

    private final Duration windowDuration;
    private final long windowDurationMs;
    private final long bucketDurationMs;
    private final int bucketCount;
    private final LongSupplier timeSupplier;

    private record BucketSlot(long epochIndex, LongAdder counter) {}

    private final AtomicReferenceArray<BucketSlot> buckets;

    /**
     * Constructs a rolling window counter with the given window duration and bucket count.
     *
     * @param windowDuration total sliding window duration (minimum 1ms)
     * @param bucketCount    number of sub-buckets in circular ring buffer (must be &gt;= 1)
     */
    public RollingWindowCounter(Duration windowDuration, int bucketCount) {
        this(windowDuration, bucketCount, System::currentTimeMillis);
    }

    /**
     * Constructs a rolling window counter using a specified Clock.
     *
     * @param windowDuration total sliding window duration
     * @param bucketCount    number of sub-buckets
     * @param clock          clock for time queries
     */
    public RollingWindowCounter(Duration windowDuration, int bucketCount, Clock clock) {
        this(windowDuration, bucketCount, Objects.requireNonNull(clock, "clock cannot be null")::millis);
    }

    /**
     * Constructs a rolling window counter using a specified epoch milliseconds supplier.
     *
     * @param windowDuration total sliding window duration
     * @param bucketCount    number of sub-buckets
     * @param timeSupplier   epoch millisecond time source
     */
    public RollingWindowCounter(Duration windowDuration, int bucketCount, LongSupplier timeSupplier) {
        Objects.requireNonNull(windowDuration, "windowDuration cannot be null");
        this.timeSupplier = Objects.requireNonNull(timeSupplier, "timeSupplier cannot be null");

        this.windowDurationMs = windowDuration.toMillis();
        if (this.windowDurationMs <= 0) {
            throw new IllegalArgumentException("windowDuration must be at least 1ms, got: " + windowDuration);
        }
        if (bucketCount <= 0) {
            throw new IllegalArgumentException("bucketCount must be >= 1, got: " + bucketCount);
        }

        this.windowDuration = windowDuration;
        this.bucketCount = bucketCount;
        this.bucketDurationMs = Math.max(1, this.windowDurationMs / bucketCount);

        this.buckets = new AtomicReferenceArray<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            this.buckets.set(i, new BucketSlot(-1L, new LongAdder()));
        }
    }

    /**
     * Factory for a 1-second rolling window partitioned into sub-buckets.
     *
     * @param bucketCount sub-bucket count (e.g., 10 for 100ms slices)
     */
    public static RollingWindowCounter perSecond(int bucketCount) {
        return new RollingWindowCounter(Duration.ofSeconds(1), bucketCount);
    }

    /**
     * Factory for a 1-minute rolling window partitioned into sub-buckets.
     *
     * @param bucketCount sub-bucket count (e.g., 60 for 1s slices)
     */
    public static RollingWindowCounter perMinute(int bucketCount) {
        return new RollingWindowCounter(Duration.ofMinutes(1), bucketCount);
    }

    /**
     * Factory for custom window and sub-bucket count.
     */
    public static RollingWindowCounter of(Duration windowDuration, int bucketCount) {
        return new RollingWindowCounter(windowDuration, bucketCount);
    }

    /**
     * Factory with custom time source for deterministic testing.
     */
    public static RollingWindowCounter of(Duration windowDuration, int bucketCount, LongSupplier timeSupplier) {
        return new RollingWindowCounter(windowDuration, bucketCount, timeSupplier);
    }

    /**
     * Records a single event (+1) in the current sub-bucket.
     */
    public void record() {
        record(1L);
    }

    /**
     * Records multiple events in the current sub-bucket lock-free.
     *
     * @param count count to add (must be &gt;= 0)
     */
    public void record(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative: " + count);
        }
        if (count == 0) {
            return;
        }

        long now = timeSupplier.getAsLong();
        long currentEpochIndex = now / bucketDurationMs;
        int ringIndex = (int) Math.floorMod(currentEpochIndex, bucketCount);

        while (true) {
            BucketSlot slot = buckets.get(ringIndex);
            if (slot.epochIndex() == currentEpochIndex) {
                slot.counter().add(count);
                return;
            } else if (slot.epochIndex() < currentEpochIndex) {
                // Stale bucket from past window: atomically install fresh bucket slot
                LongAdder freshAdder = new LongAdder();
                freshAdder.add(count);
                BucketSlot newSlot = new BucketSlot(currentEpochIndex, freshAdder);
                if (buckets.compareAndSet(ringIndex, slot, newSlot)) {
                    return;
                }
                // CAS failed: another thread updated the slot, retry
            } else {
                // Out-of-order event older than currently registered slot
                return;
            }
        }
    }

    /**
     * Returns the exact sum of events within the sliding time window.
     * Stale buckets older than (now - windowDuration) are excluded automatically.
     */
    public long sum() {
        long now = timeSupplier.getAsLong();
        long currentEpochIndex = now / bucketDurationMs;
        long oldestValidEpochIndex = currentEpochIndex - bucketCount + 1;

        long total = 0;
        for (int i = 0; i < bucketCount; i++) {
            BucketSlot slot = buckets.get(i);
            long epoch = slot.epochIndex();
            if (epoch >= oldestValidEpochIndex && epoch <= currentEpochIndex) {
                total += slot.counter().sum();
            }
        }
        return total;
    }

    /**
     * Alias for {@link #sum()}.
     */
    public long getTotalCount() {
        return sum();
    }

    /**
     * Computes the event rate per second across the active window.
     */
    public double ratePerSecond() {
        double windowSeconds = windowDurationMs / 1000.0;
        return sum() / windowSeconds;
    }

    /**
     * Computes the event rate per minute across the active window.
     */
    public double ratePerMinute() {
        return ratePerSecond() * 60.0;
    }

    /**
     * Returns count recorded in the current active sub-bucket.
     */
    public long getCurrentBucketCount() {
        long now = timeSupplier.getAsLong();
        long currentEpochIndex = now / bucketDurationMs;
        int ringIndex = (int) Math.floorMod(currentEpochIndex, bucketCount);

        BucketSlot slot = buckets.get(ringIndex);
        return (slot.epochIndex() == currentEpochIndex) ? slot.counter().sum() : 0L;
    }

    /**
     * Checks if current traffic exhibits an unexpected sudden spike.
     * Compares the current bucket's count against the average of prior buckets in the window.
     *
     * @param thresholdMultiplier spike multiplier (must be &gt; 1.0, e.g., 2.5 means 2.5x normal traffic)
     * @return true if a spike is detected, false otherwise
     */
    public boolean isSpike(double thresholdMultiplier) {
        return isSpike(thresholdMultiplier, 1.0);
    }

    /**
     * Checks if current traffic exhibits an unexpected sudden spike with a specified minimum baseline.
     *
     * @param thresholdMultiplier spike multiplier (must be &gt; 1.0)
     * @param minBaseline         minimum traffic baseline to prevent false alarms on low idle volume
     * @return true if a spike is detected, false otherwise
     */
    public boolean isSpike(double thresholdMultiplier, double minBaseline) {
        if (thresholdMultiplier <= 1.0) {
            throw new IllegalArgumentException("thresholdMultiplier must be > 1.0, got: " + thresholdMultiplier);
        }
        if (minBaseline <= 0) {
            throw new IllegalArgumentException("minBaseline must be > 0, got: " + minBaseline);
        }

        long now = timeSupplier.getAsLong();
        long currentEpochIndex = now / bucketDurationMs;
        long oldestValidEpochIndex = currentEpochIndex - bucketCount + 1;

        long currentCount = 0;
        long priorTotal = 0;
        int priorBucketCount = Math.max(1, bucketCount - 1);

        for (int i = 0; i < bucketCount; i++) {
            BucketSlot slot = buckets.get(i);
            long epoch = slot.epochIndex();
            if (epoch == currentEpochIndex) {
                currentCount += slot.counter().sum();
            } else if (epoch >= oldestValidEpochIndex && epoch < currentEpochIndex) {
                priorTotal += slot.counter().sum();
            }
        }

        double priorAverage = (double) priorTotal / priorBucketCount;
        double effectiveBaseline = Math.max(priorAverage, minBaseline);

        return (double) currentCount > (thresholdMultiplier * effectiveBaseline);
    }

    /**
     * Resets the entire circular buffer to zero.
     */
    public void reset() {
        for (int i = 0; i < bucketCount; i++) {
            buckets.set(i, new BucketSlot(-1L, new LongAdder()));
        }
    }

    public Duration getWindowDuration() {
        return windowDuration;
    }

    public int getBucketCount() {
        return bucketCount;
    }

    public Duration getBucketDuration() {
        return Duration.ofMillis(bucketDurationMs);
    }
}
