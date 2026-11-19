package com.msp.countmin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * High-throughput, thread-safe Count-Min Sketch using AtomicLongArray per row.
 * Suitable for concurrent data streaming pipelines.
 *
 * @param <T> item type
 */
public class AtomicCountMinSketch<T> {

    private final CountMinConfig config;
    private final int width;
    private final int depth;
    private final long[] seeds;
    private final AtomicLongArray[] table;
    private final LongAdder totalCount;

    private final boolean trackHeavyHitters;
    private final double phiThreshold;
    private final int maxTrackedItems;
    private final ConcurrentHashMap<T, Long> heavyCandidates;

    public AtomicCountMinSketch(CountMinConfig config, double phiThreshold, int maxTrackedItems) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.width = config.width();
        this.depth = config.depth();
        this.seeds = generateSeeds(depth);
        this.table = new AtomicLongArray[depth];
        for (int i = 0; i < depth; i++) {
            this.table[i] = new AtomicLongArray(width);
        }
        this.totalCount = new LongAdder();

        if (phiThreshold > 0.0) {
            if (phiThreshold >= 1.0) {
                throw new IllegalArgumentException("phiThreshold must be in (0, 1): " + phiThreshold);
            }
            this.trackHeavyHitters = true;
            this.phiThreshold = phiThreshold;
            this.maxTrackedItems = maxTrackedItems > 0 ? maxTrackedItems : (int) Math.ceil(1.0 / phiThreshold) * 2;
            this.heavyCandidates = new ConcurrentHashMap<>();
        } else {
            this.trackHeavyHitters = false;
            this.phiThreshold = 0.0;
            this.maxTrackedItems = 0;
            this.heavyCandidates = null;
        }
    }

    public AtomicCountMinSketch(CountMinConfig config) {
        this(config, 0.0, 0);
    }

    public static <T> AtomicCountMinSketch<T> create(double epsilon, double delta) {
        return new AtomicCountMinSketch<>(CountMinConfig.of(epsilon, delta));
    }

    public static <T> AtomicCountMinSketch<T> create(int width, int depth) {
        return new AtomicCountMinSketch<>(CountMinConfig.ofDimensions(width, depth));
    }

    public static <T> AtomicCountMinSketch<T> createWithHeavyHitters(double epsilon, double delta, double phiThreshold) {
        return new AtomicCountMinSketch<>(CountMinConfig.of(epsilon, delta), phiThreshold, 0);
    }

    public CountMinConfig getConfig() {
        return config;
    }

    public int getWidth() {
        return width;
    }

    public int getDepth() {
        return depth;
    }

    public long getTotalCount() {
        return totalCount.sum();
    }

    public void update(T item) {
        update(item, 1L);
    }

    public void update(T item, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative: " + count);
        }
        if (count == 0) return;

        long itemHash = CountMinSketch.hash64(item);
        for (int j = 0; j < depth; j++) {
            int bucket = bucketIndex(itemHash, j);
            table[j].addAndGet(bucket, count);
        }
        totalCount.add(count);

        if (trackHeavyHitters) {
            recordHeavyCandidate(item);
        }
    }

    /**
     * Estimates frequency of item concurrently.
     */
    public long estimateFrequency(T item) {
        long itemHash = CountMinSketch.hash64(item);
        long min = Long.MAX_VALUE;
        for (int j = 0; j < depth; j++) {
            int bucket = bucketIndex(itemHash, j);
            long val = table[j].get(bucket);
            if (val < min) {
                min = val;
            }
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    /**
     * Returns heavy hitters exceeding the configured phi fraction of total items.
     */
    public List<HeavyHitter<T>> getHeavyHitters() {
        if (!trackHeavyHitters) {
            return Collections.emptyList();
        }
        long total = totalCount.sum();
        if (total == 0) return Collections.emptyList();

        long threshold = (long) Math.ceil(phiThreshold * total);
        List<HeavyHitter<T>> result = new ArrayList<>();
        for (Map.Entry<T, Long> entry : heavyCandidates.entrySet()) {
            long freq = estimateFrequency(entry.getKey());
            if (freq >= threshold) {
                double ratio = (double) freq / total;
                result.add(new HeavyHitter<>(entry.getKey(), freq, ratio));
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Returns top-K frequent items among tracked candidates.
     */
    public List<HeavyHitter<T>> getTopK(int k) {
        if (!trackHeavyHitters || k <= 0 || heavyCandidates.isEmpty()) {
            return Collections.emptyList();
        }
        long total = totalCount.sum();
        if (total == 0) return Collections.emptyList();

        List<HeavyHitter<T>> list = new ArrayList<>();
        for (Map.Entry<T, Long> entry : heavyCandidates.entrySet()) {
            long freq = estimateFrequency(entry.getKey());
            double ratio = (double) freq / total;
            list.add(new HeavyHitter<>(entry.getKey(), freq, ratio));
        }
        Collections.sort(list);
        return list.subList(0, Math.min(k, list.size()));
    }

    public void clear() {
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < width; i++) {
                table[j].set(i, 0L);
            }
        }
        totalCount.reset();
        if (trackHeavyHitters) {
            heavyCandidates.clear();
        }
    }

    private void recordHeavyCandidate(T item) {
        long est = estimateFrequency(item);
        long total = totalCount.sum();
        long threshold = (long) Math.ceil(phiThreshold * total);
        if (est >= threshold || heavyCandidates.containsKey(item)) {
            heavyCandidates.put(item, est);
            if (heavyCandidates.size() > maxTrackedItems) {
                heavyCandidates.entrySet().removeIf(e -> estimateFrequency(e.getKey()) < (long) Math.ceil(phiThreshold * totalCount.sum()));
            }
        }
    }

    private int bucketIndex(long itemHash, int row) {
        long mixed = CountMinSketch.splitmix64(itemHash ^ seeds[row]);
        int b = (int) (mixed % width);
        return b < 0 ? b + width : b;
    }

    private static long[] generateSeeds(int depth) {
        long[] seeds = new long[depth];
        for (int i = 0; i < depth; i++) {
            seeds[i] = CountMinSketch.splitmix64(0x9e3779b97f4a7c15L + (long) i * 0x6a09e667f3bcc908L);
        }
        return seeds;
    }
}
