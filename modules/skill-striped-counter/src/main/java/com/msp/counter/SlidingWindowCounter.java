package com.msp.counter;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Thread-safe sliding-window event counter and rate tracker.
 * <p>
 * Divides a sliding time window into multiple discrete sub-buckets.
 * Provides high-throughput event recording with zero-background-thread sliding expiration,
 * accurate rate calculations (per-second and per-minute), and smooth window rollover.
 * </p>
 */
public class SlidingWindowCounter {

    private final Duration windowDuration;
    private final long windowDurationMs;
    private final long bucketDurationMs;
    private final Bucket[] buckets;
    private final LongSupplier timeSupplier;

    private static final class Bucket {
        final AtomicLong bucketIndex = new AtomicLong(-1L);
        final StripedCounter counter = new StripedCounter(4);
    }

    /**
     * Creates a sliding window counter with specified window duration and sub-bucket count.
     *
     * @param windowDuration total sliding window duration (must be at least 1ms)
     * @param bucketCount    number of sub-buckets (must be &gt;= 1)
     */
    public SlidingWindowCounter(Duration windowDuration, int bucketCount) {
        this(windowDuration, bucketCount, System::currentTimeMillis);
    }

    /**
     * Creates a sliding window counter with a custom time source (useful for deterministic testing).
     *
     * @param windowDuration total sliding window duration
     * @param bucketCount    number of sub-buckets
     * @param timeSupplier   supplier of current epoch milliseconds
     */
    public SlidingWindowCounter(Duration windowDuration, int bucketCount, LongSupplier timeSupplier) {
        Objects.requireNonNull(windowDuration, "windowDuration must not be null");
        this.timeSupplier = Objects.requireNonNull(timeSupplier, "timeSupplier must not be null");

        this.windowDurationMs = windowDuration.toMillis();
        if (this.windowDurationMs <= 0) {
            throw new IllegalArgumentException("windowDuration must be at least 1 millisecond");
        }
        if (bucketCount <= 0) {
            throw new IllegalArgumentException("bucketCount must be positive: " + bucketCount);
        }

        this.windowDuration = windowDuration;
        this.bucketDurationMs = Math.max(1, this.windowDurationMs / bucketCount);
        this.buckets = new Bucket[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            this.buckets[i] = new Bucket();
        }
    }

    /**
     * Factory for a 1-second sliding window partitioned into sub-buckets.
     *
     * @param subBuckets number of slices per second (e.g. 10 for 100ms resolution)
     * @return new {@link SlidingWindowCounter}
     */
    public static SlidingWindowCounter perSecond(int subBuckets) {
        return new SlidingWindowCounter(Duration.ofSeconds(1), subBuckets);
    }

    /**
     * Factory for a 1-minute (60 seconds) sliding window partitioned into sub-buckets.
     *
     * @param subBuckets number of slices per minute (e.g. 60 for 1s resolution)
     * @return new {@link SlidingWindowCounter}
     */
    public static SlidingWindowCounter perMinute(int subBuckets) {
        return new SlidingWindowCounter(Duration.ofMinutes(1), subBuckets);
    }

    /**
     * Factory for an arbitrary window and slice count.
     *
     * @param windowDuration window duration
     * @param subBuckets     slice count
     * @return new {@link SlidingWindowCounter}
     */
    public static SlidingWindowCounter of(Duration windowDuration, int subBuckets) {
        return new SlidingWindowCounter(windowDuration, subBuckets);
    }

    /**
     * Factory for an arbitrary window and slice count with custom time supplier.
     *
     * @param windowDuration window duration
     * @param subBuckets     slice count
     * @param timeSupplier   time supplier in milliseconds
     * @return new {@link SlidingWindowCounter}
     */
    public static SlidingWindowCounter of(Duration windowDuration, int subBuckets, LongSupplier timeSupplier) {
        return new SlidingWindowCounter(windowDuration, subBuckets, timeSupplier);
    }

    /**
     * Records a single event in the current window bucket.
     */
    public void record() {
        record(1L);
    }

    /**
     * Records multiple events in the current window bucket.
     *
     * @param count number of events to record (must be &gt;= 0)
     */
    public void record(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        if (count == 0) {
            return;
        }

        long now = timeSupplier.getAsLong();
        long currentBucketIndex = now / bucketDurationMs;
        int arrayIndex = (int) Math.floorMod(currentBucketIndex, buckets.length);

        Bucket bucket = buckets[arrayIndex];
        if (bucket.bucketIndex.get() != currentBucketIndex) {
            synchronized (bucket) {
                if (bucket.bucketIndex.get() != currentBucketIndex) {
                    bucket.counter.reset();
                    bucket.bucketIndex.set(currentBucketIndex);
                }
            }
        }
        bucket.counter.add(count);
    }

    /**
     * Calculates the total event count in the active sliding window up to the current moment.
     *
     * @return total event count in sliding window
     */
    public long getTotalCount() {
        long now = timeSupplier.getAsLong();
        long currentBucketIndex = now / bucketDurationMs;
        long minValidBucketIndex = currentBucketIndex - buckets.length + 1;

        long sum = 0;
        for (Bucket bucket : buckets) {
            long idx = bucket.bucketIndex.get();
            if (idx >= minValidBucketIndex && idx <= currentBucketIndex) {
                sum += bucket.counter.sum();
            }
        }
        return sum;
    }

    /**
     * Computes the event rate per second over the configured window duration.
     *
     * @return event rate per second
     */
    public double getRatePerSecond() {
        long count = getTotalCount();
        double windowSeconds = windowDurationMs / 1000.0;
        return count / windowSeconds;
    }

    /**
     * Computes the event rate per minute over the configured window duration.
     *
     * @return event rate per minute
     */
    public double getRatePerMinute() {
        return getRatePerSecond() * 60.0;
    }

    /**
     * Resets all sub-buckets and event counts to zero.
     */
    public void reset() {
        for (Bucket bucket : buckets) {
            synchronized (bucket) {
                bucket.bucketIndex.set(-1L);
                bucket.counter.reset();
            }
        }
    }

    /**
     * Returns the total window duration.
     */
    public Duration getWindowDuration() {
        return windowDuration;
    }

    /**
     * Returns the duration of each individual sub-bucket.
     */
    public Duration getBucketDuration() {
        return Duration.ofMillis(bucketDurationMs);
    }

    /**
     * Returns the total number of sub-buckets.
     */
    public int getBucketCount() {
        return buckets.length;
    }
}
