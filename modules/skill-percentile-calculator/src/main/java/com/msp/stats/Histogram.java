package com.msp.stats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Frequency distribution histogram with uniform bucket widths.
 */
public final class Histogram {

    private final double min;
    private final double max;
    private final double bucketWidth;
    private final long totalCount;
    private final List<Bucket> buckets;

    private Histogram(double min, double max, double bucketWidth, long totalCount, List<Bucket> buckets) {
        this.min = min;
        this.max = max;
        this.bucketWidth = bucketWidth;
        this.totalCount = totalCount;
        this.buckets = Collections.unmodifiableList(buckets);
    }

    /**
     * Builds a histogram from a collection of values and bucket count.
     *
     * @param values      data points
     * @param bucketCount number of bins (must be >= 1)
     * @return constructed Histogram
     */
    public static Histogram of(Collection<Double> values, int bucketCount) {
        if (bucketCount <= 0) {
            throw new IllegalArgumentException("bucketCount must be positive, got: " + bucketCount);
        }
        if (values == null || values.isEmpty()) {
            return new Histogram(0.0, 0.0, 0.0, 0, List.of());
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int validCount = 0;

        for (Double v : values) {
            if (v == null || Double.isNaN(v) || Double.isInfinite(v)) {
                throw new IllegalArgumentException("Invalid value encountered: " + v);
            }
            if (v < min) min = v;
            if (v > max) max = v;
            validCount++;
        }

        return ofExplicit(values, min, max, bucketCount, validCount);
    }

    /**
     * Builds a histogram with explicitly bounded range.
     */
    public static Histogram of(Collection<Double> values, double rangeMin, double rangeMax, int bucketCount) {
        if (bucketCount <= 0) {
            throw new IllegalArgumentException("bucketCount must be positive: " + bucketCount);
        }
        if (rangeMin > rangeMax) {
            throw new IllegalArgumentException("rangeMin cannot exceed rangeMax: " + rangeMin + " > " + rangeMax);
        }
        int count = values != null ? values.size() : 0;
        return ofExplicit(values != null ? values : List.of(), rangeMin, rangeMax, bucketCount, count);
    }

    private static Histogram ofExplicit(Collection<Double> values, double min, double max, int bucketCount, int totalCount) {
        if (totalCount == 0) {
            return new Histogram(min, max, 0.0, 0, List.of());
        }

        if (Double.compare(min, max) == 0) {
            Bucket single = new Bucket(min, max, totalCount, 100.0);
            return new Histogram(min, max, 0.0, totalCount, List.of(single));
        }

        double width = (max - min) / bucketCount;
        long[] counts = new long[bucketCount];

        for (Double v : values) {
            if (v < min || v > max) {
                // Out of custom bounds: clamp or skip based on behavior
                continue;
            }
            int idx = (int) ((v - min) / width);
            if (idx >= bucketCount) {
                idx = bucketCount - 1; // max bound falls into final bucket
            } else if (idx < 0) {
                idx = 0;
            }
            counts[idx]++;
        }

        List<Bucket> bucketList = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            double lower = min + i * width;
            double upper = (i == bucketCount - 1) ? max : min + (i + 1) * width;
            double pct = totalCount > 0 ? (counts[i] * 100.0 / totalCount) : 0.0;
            bucketList.add(new Bucket(lower, upper, counts[i], pct));
        }

        return new Histogram(min, max, width, totalCount, bucketList);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getBucketWidth() {
        return bucketWidth;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public List<Bucket> getBuckets() {
        return buckets;
    }

    /**
     * Finds the bucket that contains the given value.
     */
    public Optional<Bucket> findBucket(double value) {
        if (buckets.isEmpty()) {
            return Optional.empty();
        }
        for (int i = 0; i < buckets.size(); i++) {
            boolean isLast = (i == buckets.size() - 1);
            Bucket b = buckets.get(i);
            if (b.contains(value, isLast)) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    /**
     * Renders a human-readable ASCII representation of the histogram.
     */
    public String renderAscii(int maxBarLength) {
        if (buckets.isEmpty()) {
            return "(Empty Histogram)";
        }
        long maxCount = 0;
        for (Bucket b : buckets) {
            if (b.count() > maxCount) maxCount = b.count();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Histogram [Total: %d, Range: [%.2f, %.2f]]%n", totalCount, min, max));
        for (Bucket b : buckets) {
            int barLen = (maxCount > 0) ? (int) Math.round((double) b.count() / maxCount * maxBarLength) : 0;
            String bar = "█".repeat(barLen);
            sb.append(String.format("[%7.2f - %7.2f]: %6d (%5.1f%%) %s%n",
                    b.lowerBound(), b.upperBound(), b.count(), b.percentage(), bar));
        }
        return sb.toString();
    }
}
