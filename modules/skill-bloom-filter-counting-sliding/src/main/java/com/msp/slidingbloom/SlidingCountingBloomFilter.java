package com.msp.slidingbloom;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Sliding-window Counting Bloom Filter using time-bucketed ring buffers to automatically expire stale elements.
 * <p>
 * Zero 3rd-party runtime dependencies. Uses pure Java Murmur3 dual-hashing (Kirsch-Mitzenmacher optimization).
 * Thread-safe for concurrent read and write operations.
 *
 * @param <T> element type
 */
public class SlidingCountingBloomFilter<T> {

    private final int expectedInsertionsPerWindow;
    private final double fpp;
    private final Duration windowDuration;
    private final int bucketCount;
    private final long bucketDurationMs;
    private final int m; // number of counter bins per bucket
    private final int k; // number of hash functions
    private final Function<T, byte[]> byteConverter;
    private final Clock clock;

    private final Bucket[] buckets;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private static final class Bucket {
        long bucketEpoch = -1;
        final int[] counters;
        int itemCount = 0;

        Bucket(int size) {
            this.counters = new int[size];
        }

        void reset(long epoch) {
            this.bucketEpoch = epoch;
            Arrays.fill(counters, 0);
            this.itemCount = 0;
        }

        void clear() {
            this.bucketEpoch = -1;
            Arrays.fill(counters, 0);
            this.itemCount = 0;
        }
    }

    /**
     * Creates a sliding counting bloom filter with system UTC clock.
     *
     * @param expectedInsertionsPerWindow expected maximum insertions during any sliding window duration
     * @param fpp                        desired false positive probability (e.g. 0.01 for 1%)
     * @param windowDuration             duration of the sliding window
     * @param bucketCount                number of ring-buffer time buckets dividing the window
     */
    public SlidingCountingBloomFilter(int expectedInsertionsPerWindow, double fpp, Duration windowDuration, int bucketCount) {
        this(expectedInsertionsPerWindow, fpp, windowDuration, bucketCount, defaultByteConverter(), Clock.systemUTC());
    }

    /**
     * Creates a sliding counting bloom filter with a custom clock (useful for deterministic unit testing).
     */
    public SlidingCountingBloomFilter(int expectedInsertionsPerWindow, double fpp, Duration windowDuration, int bucketCount, Clock clock) {
        this(expectedInsertionsPerWindow, fpp, windowDuration, bucketCount, defaultByteConverter(), clock);
    }

    /**
     * Creates a sliding counting bloom filter with custom element byte converter and clock.
     */
    public SlidingCountingBloomFilter(
            int expectedInsertionsPerWindow,
            double fpp,
            Duration windowDuration,
            int bucketCount,
            Function<T, byte[]> byteConverter,
            Clock clock
    ) {
        if (expectedInsertionsPerWindow <= 0) {
            throw new IllegalArgumentException("expectedInsertionsPerWindow must be > 0: " + expectedInsertionsPerWindow);
        }
        if (fpp <= 0.0 || fpp >= 1.0 || Double.isNaN(fpp)) {
            throw new IllegalArgumentException("fpp must be between 0.0 and 1.0: " + fpp);
        }
        Objects.requireNonNull(windowDuration, "windowDuration must not be null");
        if (windowDuration.isNegative() || windowDuration.isZero()) {
            throw new IllegalArgumentException("windowDuration must be positive");
        }
        if (bucketCount <= 0) {
            throw new IllegalArgumentException("bucketCount must be > 0: " + bucketCount);
        }

        this.expectedInsertionsPerWindow = expectedInsertionsPerWindow;
        this.fpp = fpp;
        this.windowDuration = windowDuration;
        this.bucketCount = bucketCount;
        this.bucketDurationMs = Math.max(1, windowDuration.toMillis() / bucketCount);
        this.byteConverter = Objects.requireNonNull(byteConverter, "byteConverter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");

        // Optimal Bloom Filter size m = ceil(-n * ln(p) / (ln(2)^2))
        int calculatedM = (int) Math.ceil(-1.0 * expectedInsertionsPerWindow * Math.log(fpp) / (Math.log(2.0) * Math.log(2.0)));
        this.m = Math.max(64, calculatedM);

        // Optimal number of hash functions k = round((m / n) * ln(2))
        int calculatedK = (int) Math.max(1, Math.round(((double) this.m / expectedInsertionsPerWindow) * Math.log(2.0)));
        this.k = calculatedK;

        this.buckets = new Bucket[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            this.buckets[i] = new Bucket(this.m);
        }
    }

    /**
     * Adds an item into the current active time bucket, incrementing its counting bins.
     *
     * @param item the item to add
     */
    public void add(T item) {
        Objects.requireNonNull(item, "item must not be null");
        byte[] bytes = byteConverter.apply(item);
        long[] hashes = Murmur3.hash128(bytes, 0, bytes.length, 0L);

        rwLock.writeLock().lock();
        try {
            long currentEpoch = currentEpoch();
            int ringIndex = (int) Math.floorMod(currentEpoch, bucketCount);
            Bucket activeBucket = buckets[ringIndex];

            if (activeBucket.bucketEpoch != currentEpoch) {
                activeBucket.reset(currentEpoch);
            }

            for (int i = 0; i < k; i++) {
                long combinedHash = hashes[0] + (long) i * hashes[1];
                int binIndex = (int) Math.floorMod(combinedHash, m);
                if (activeBucket.counters[binIndex] < Integer.MAX_VALUE) {
                    activeBucket.counters[binIndex]++;
                }
            }
            activeBucket.itemCount++;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Checks if the item might have been added to any currently active, non-expired bucket.
     *
     * @param item the item to query
     * @return {@code true} if the item might exist; {@code false} if definitely absent from all active buckets
     */
    public boolean mightContain(T item) {
        if (item == null) {
            return false;
        }
        byte[] bytes = byteConverter.apply(item);
        long[] hashes = Murmur3.hash128(bytes, 0, bytes.length, 0L);

        rwLock.readLock().lock();
        try {
            long currentEpoch = currentEpoch();
            long minValidEpoch = currentEpoch - bucketCount + 1;

            for (Bucket bucket : buckets) {
                if (bucket.bucketEpoch >= minValidEpoch && bucket.bucketEpoch <= currentEpoch && bucket.itemCount > 0) {
                    boolean matchInBucket = true;
                    for (int i = 0; i < k; i++) {
                        long combinedHash = hashes[0] + (long) i * hashes[1];
                        int binIndex = (int) Math.floorMod(combinedHash, m);
                        if (bucket.counters[binIndex] <= 0) {
                            matchInBucket = false;
                            break;
                        }
                    }
                    if (matchInBucket) {
                        return true;
                    }
                }
            }
            return false;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Clears all buckets that have slid past the active sliding window duration.
     *
     * @return the number of expired buckets purged
     */
    public int purgeExpiredBuckets() {
        rwLock.writeLock().lock();
        try {
            long currentEpoch = currentEpoch();
            long minValidEpoch = currentEpoch - bucketCount + 1;
            int purged = 0;

            for (Bucket bucket : buckets) {
                if (bucket.bucketEpoch != -1 && bucket.bucketEpoch < minValidEpoch) {
                    bucket.clear();
                    purged++;
                }
            }
            return purged;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Returns the count of active, non-expired buckets currently in the sliding window.
     */
    public int activeBucketCount() {
        rwLock.readLock().lock();
        try {
            long currentEpoch = currentEpoch();
            long minValidEpoch = currentEpoch - bucketCount + 1;
            int count = 0;
            for (Bucket bucket : buckets) {
                if (bucket.bucketEpoch >= minValidEpoch && bucket.bucketEpoch <= currentEpoch && bucket.itemCount > 0) {
                    count++;
                }
            }
            return count;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Clears all buckets and resets all counters.
     */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            for (Bucket bucket : buckets) {
                bucket.clear();
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public int bitSize() {
        return m;
    }

    public int hashCount() {
        return k;
    }

    public Duration windowDuration() {
        return windowDuration;
    }

    public int bucketCount() {
        return bucketCount;
    }

    public double fpp() {
        return fpp;
    }

    private long currentEpoch() {
        return clock.millis() / bucketDurationMs;
    }

    @SuppressWarnings("unchecked")
    private static <T> Function<T, byte[]> defaultByteConverter() {
        return item -> {
            if (item instanceof byte[] b) {
                return b;
            }
            if (item instanceof String s) {
                return s.getBytes(StandardCharsets.UTF_8);
            }
            return item.toString().getBytes(StandardCharsets.UTF_8);
        };
    }
}
