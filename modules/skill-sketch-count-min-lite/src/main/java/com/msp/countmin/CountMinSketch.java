package com.msp.countmin;

import java.util.*;

/**
 * Probabilistic Count-Min Sketch data structure for frequency estimation and heavy hitters detection.
 * Provides (epsilon, delta) error guarantees: estimated_frequency &lt;= actual_frequency + epsilon * N
 * with probability at least (1 - delta).
 *
 * @param <T> item type
 */
public class CountMinSketch<T> {

    private final CountMinConfig config;
    private final int width;
    private final int depth;
    private final long[] seeds;
    private final long[][] table;
    private long totalCount;

    // Optional Heavy Hitters tracking
    private final boolean trackHeavyHitters;
    private final double phiThreshold;
    private final int maxTrackedItems;
    private final Map<T, Long> heavyCandidates;

    public CountMinSketch(CountMinConfig config, double phiThreshold, int maxTrackedItems) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.width = config.width();
        this.depth = config.depth();
        this.table = new long[depth][width];
        this.seeds = generateSeeds(depth);
        this.totalCount = 0;

        if (phiThreshold > 0.0) {
            if (phiThreshold >= 1.0) {
                throw new IllegalArgumentException("phiThreshold must be in (0, 1): " + phiThreshold);
            }
            this.trackHeavyHitters = true;
            this.phiThreshold = phiThreshold;
            this.maxTrackedItems = maxTrackedItems > 0 ? maxTrackedItems : (int) Math.ceil(1.0 / phiThreshold) * 2;
            this.heavyCandidates = new HashMap<>();
        } else {
            this.trackHeavyHitters = false;
            this.phiThreshold = 0.0;
            this.maxTrackedItems = 0;
            this.heavyCandidates = Collections.emptyMap();
        }
    }

    public CountMinSketch(CountMinConfig config) {
        this(config, 0.0, 0);
    }

    public static <T> CountMinSketch<T> create(double epsilon, double delta) {
        return new CountMinSketch<>(CountMinConfig.of(epsilon, delta));
    }

    public static <T> CountMinSketch<T> create(int width, int depth) {
        return new CountMinSketch<>(CountMinConfig.ofDimensions(width, depth));
    }

    public static <T> CountMinSketch<T> createWithHeavyHitters(double epsilon, double delta, double phiThreshold) {
        return new CountMinSketch<>(CountMinConfig.of(epsilon, delta), phiThreshold, 0);
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
        return totalCount;
    }

    /**
     * Updates sketch with 1 occurrence of the item.
     */
    public void update(T item) {
        update(item, 1L);
    }

    /**
     * Updates sketch with given count of the item.
     */
    public void update(T item, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative: " + count);
        }
        if (count == 0) return;

        long itemHash = hash64(item);
        for (int j = 0; j < depth; j++) {
            int bucket = bucketIndex(itemHash, j);
            table[j][bucket] += count;
        }
        totalCount += count;

        if (trackHeavyHitters) {
            recordHeavyCandidate(item);
        }
    }

    /**
     * Updates sketch using Conservative Update optimization (Estan & Varghese 2002).
     * Strictly avoids unnecessary overestimation without violating theoretical error bounds.
     */
    public void updateConservative(T item, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative: " + count);
        }
        if (count == 0) return;

        long itemHash = hash64(item);
        long minCount = estimateFrequency(item);
        long target = minCount + count;

        for (int j = 0; j < depth; j++) {
            int bucket = bucketIndex(itemHash, j);
            if (table[j][bucket] < target) {
                table[j][bucket] = target;
            }
        }
        totalCount += count;

        if (trackHeavyHitters) {
            recordHeavyCandidate(item);
        }
    }

    /**
     * Estimates the occurrence frequency of the item (minimum counter across all rows).
     */
    public long estimateFrequency(T item) {
        long itemHash = hash64(item);
        long min = Long.MAX_VALUE;
        for (int j = 0; j < depth; j++) {
            int bucket = bucketIndex(itemHash, j);
            long val = table[j][bucket];
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
        if (!trackHeavyHitters || totalCount == 0) {
            return Collections.emptyList();
        }
        long threshold = (long) Math.ceil(phiThreshold * totalCount);
        List<HeavyHitter<T>> result = new ArrayList<>();
        for (Map.Entry<T, Long> entry : heavyCandidates.entrySet()) {
            long freq = estimateFrequency(entry.getKey());
            if (freq >= threshold) {
                double ratio = (double) freq / totalCount;
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
        if (k <= 0 || heavyCandidates.isEmpty() || totalCount == 0) {
            return Collections.emptyList();
        }
        List<HeavyHitter<T>> list = new ArrayList<>();
        for (Map.Entry<T, Long> entry : heavyCandidates.entrySet()) {
            long freq = estimateFrequency(entry.getKey());
            double ratio = (double) freq / totalCount;
            list.add(new HeavyHitter<>(entry.getKey(), freq, ratio));
        }
        Collections.sort(list);
        return list.subList(0, Math.min(k, list.size()));
    }

    /**
     * Merges another Count-Min sketch with identical dimensions into a new sketch.
     */
    public CountMinSketch<T> merge(CountMinSketch<T> other) {
        Objects.requireNonNull(other, "other sketch must not be null");
        if (this.width != other.width || this.depth != other.depth) {
            throw new IllegalArgumentException(String.format(
                    "Dimension mismatch: (%dx%d) vs (%dx%d)", this.width, this.depth, other.width, other.depth));
        }

        CountMinSketch<T> merged = new CountMinSketch<>(this.config, this.phiThreshold, this.maxTrackedItems);
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < width; i++) {
                merged.table[j][i] = this.table[j][i] + other.table[j][i];
            }
        }
        merged.totalCount = this.totalCount + other.totalCount;

        if (this.trackHeavyHitters) {
            merged.heavyCandidates.putAll(this.heavyCandidates);
            merged.heavyCandidates.putAll(other.heavyCandidates);
        }

        return merged;
    }

    /**
     * Resets all counters and tracked heavy hitters.
     */
    public void clear() {
        for (int j = 0; j < depth; j++) {
            Arrays.fill(table[j], 0L);
        }
        totalCount = 0L;
        if (trackHeavyHitters) {
            heavyCandidates.clear();
        }
    }

    private void recordHeavyCandidate(T item) {
        long est = estimateFrequency(item);
        long threshold = (long) Math.ceil(phiThreshold * totalCount);
        if (est >= threshold || heavyCandidates.containsKey(item)) {
            heavyCandidates.put(item, est);
            if (heavyCandidates.size() > maxTrackedItems) {
                pruneCandidates();
            }
        }
    }

    private void pruneCandidates() {
        long threshold = (long) Math.ceil(phiThreshold * totalCount);
        heavyCandidates.entrySet().removeIf(e -> estimateFrequency(e.getKey()) < threshold);
    }

    private int bucketIndex(long itemHash, int row) {
        long mixed = splitmix64(itemHash ^ seeds[row]);
        int b = (int) (mixed % width);
        return b < 0 ? b + width : b;
    }

    private static long[] generateSeeds(int depth) {
        long[] seeds = new long[depth];
        for (int i = 0; i < depth; i++) {
            seeds[i] = splitmix64(0x9e3779b97f4a7c15L + (long) i * 0x6a09e667f3bcc908L);
        }
        return seeds;
    }

    public static long hash64(Object item) {
        if (item == null) return 0L;
        if (item instanceof byte[] b) {
            long h = 0xcbf29ce484222325L;
            for (byte x : b) {
                h ^= (x & 0xFF);
                h *= 0x100000001b3L;
            }
            return splitmix64(h);
        }
        if (item instanceof Long l) return splitmix64(l);
        if (item instanceof Integer i) return splitmix64((long) i);
        if (item instanceof String s) {
            long h = 0xcbf29ce484222325L;
            for (int i = 0; i < s.length(); i++) {
                h ^= s.charAt(i);
                h *= 0x100000001b3L;
            }
            return splitmix64(h);
        }
        return splitmix64((long) item.hashCode());
    }

    public static long splitmix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
