package com.msp.tdigest;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Thread-safe Merging T-Digest streaming quantile estimator using Dunning's {@code K_1}
 * (arcsin) scale function for high accuracy at extreme tail quantiles (p95, p99, p99.9).
 *
 * <p>Supports weighted streaming ingestion, associative merging across distributed workers,
 * quantile and CDF queries, and compact binary serialization.
 */
public final class TDigest {

    public static final double DEFAULT_COMPRESSION = 100.0;
    private static final double MIN_COMPRESSION = 10.0;
    private static final int BINARY_MAGIC = 0x54444731; // "TDG1"
    private static final byte BINARY_VERSION = 1;

    private final double compression;
    private final int bufferCapacity;

    // Merged centroids (sorted ascending by mean)
    private double[] mergedMeans;
    private double[] mergedWeights;
    private int mergedCount;
    private double mergedWeight;

    // Unmerged incoming buffer
    private double[] tempMeans;
    private double[] tempWeights;
    private int tempCount;
    private double tempWeight;

    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;
    private boolean reverseNextMerge = false;

    private TDigest(double compression) {
        if (!Double.isFinite(compression) || compression < MIN_COMPRESSION) {
            throw new IllegalArgumentException("compression must be finite and >= " + MIN_COMPRESSION + ": " + compression);
        }
        this.compression = compression;
        int initialMergedCap = Math.max(64, (int) Math.ceil(compression * 2));
        this.bufferCapacity = Math.max(256, (int) Math.ceil(compression * 5));
        this.mergedMeans = new double[initialMergedCap];
        this.mergedWeights = new double[initialMergedCap];
        this.tempMeans = new double[bufferCapacity];
        this.tempWeights = new double[bufferCapacity];
    }

    /**
     * Creates a new T-Digest with the default compression factor ({@value #DEFAULT_COMPRESSION}).
     */
    public static TDigest create() {
        return new TDigest(DEFAULT_COMPRESSION);
    }

    /**
     * Creates a new T-Digest with the specified compression factor {@code delta} ({@code >= 10}).
     * Higher compression yields higher quantile accuracy at the cost of more centroids.
     */
    public static TDigest create(double compression) {
        return new TDigest(compression);
    }

    /**
     * Adds a single observation with weight {@code 1.0}.
     */
    public synchronized void add(double value) {
        add(value, 1.0);
    }

    /**
     * Adds an observation with a positive weight.
     */
    public synchronized void add(double value, double weight) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite: " + value);
        }
        if (!Double.isFinite(weight) || weight <= 0.0) {
            throw new IllegalArgumentException("weight must be finite and > 0: " + weight);
        }

        if (tempCount >= bufferCapacity) {
            compressLocked();
        }

        tempMeans[tempCount] = value;
        tempWeights[tempCount] = weight;
        tempCount++;
        tempWeight += weight;

        if (value < min) {
            min = value;
        }
        if (value > max) {
            max = value;
        }
    }

    /**
     * Merges all centroids and statistics from {@code other} into this digest.
     */
    public synchronized void merge(TDigest other) {
        Objects.requireNonNull(other, "other must not be null");
        if (other == this) {
            // Snapshot self to avoid concurrent modification of same arrays
            List<Centroid> snapshot = centroids();
            for (Centroid c : snapshot) {
                add(c.mean(), c.weight());
            }
            return;
        }

        double otherMin;
        double otherMax;
        List<Centroid> otherCentroids;
        synchronized (other) {
            if (other.countLocked() == 0.0) {
                return;
            }
            other.compressLocked();
            otherMin = other.min;
            otherMax = other.max;
            otherCentroids = new ArrayList<>(other.mergedCount);
            for (int i = 0; i < other.mergedCount; i++) {
                otherCentroids.add(new Centroid(other.mergedMeans[i], other.mergedWeights[i]));
            }
        }

        for (Centroid c : otherCentroids) {
            if (tempCount >= bufferCapacity) {
                compressLocked();
            }
            tempMeans[tempCount] = c.mean();
            tempWeights[tempCount] = c.weight();
            tempCount++;
            tempWeight += c.weight();
        }
        if (otherMin < this.min) {
            this.min = otherMin;
        }
        if (otherMax > this.max) {
            this.max = otherMax;
        }
        compressLocked();
    }

    /**
     * Forces immediate compression of any buffered observations into sorted centroids.
     */
    public synchronized void compress() {
        compressLocked();
    }

    /**
     * Estimates the value at quantile {@code q} in {@code [0.0, 1.0]}.
     * Returns {@link Double#NaN} if the digest is empty.
     */
    public synchronized double quantile(double q) {
        if (!Double.isFinite(q) || q < 0.0 || q > 1.0) {
            throw new IllegalArgumentException("q must be in [0.0, 1.0]: " + q);
        }
        compressLocked();
        if (mergedCount == 0) {
            return Double.NaN;
        }
        if (mergedCount == 1) {
            return mergedMeans[0];
        }
        if (q == 0.0) {
            return min;
        }
        if (q == 1.0) {
            return max;
        }

        double total = mergedWeight;
        double index = q * total;

        // Left tail: between min and first centroid
        if (index < mergedWeights[0] / 2.0) {
            if (mergedWeights[0] <= 1.0) {
                return min;
            }
            return min + 2.0 * index / mergedWeights[0] * (mergedMeans[0] - min);
        }

        // Right tail: between last centroid and max
        int last = mergedCount - 1;
        if (index > total - mergedWeights[last] / 2.0) {
            if (mergedWeights[last] <= 1.0) {
                return max;
            }
            double distFromEnd = total - index;
            return max - 2.0 * distFromEnd / mergedWeights[last] * (max - mergedMeans[last]);
        }

        // Interior linear interpolation between adjacent centroid midpoints
        double weightSoFar = mergedWeights[0] / 2.0;
        for (int i = 0; i < last; i++) {
            double dw = (mergedWeights[i] + mergedWeights[i + 1]) / 2.0;
            if (weightSoFar + dw > index) {
                double z1 = index - weightSoFar;
                double z2 = weightSoFar + dw - index;
                return weightedAverage(mergedMeans[i], z2, mergedMeans[i + 1], z1);
            }
            weightSoFar += dw;
        }

        return mergedMeans[last];
    }

    public double p50() {
        return quantile(0.50);
    }

    public double p90() {
        return quantile(0.90);
    }

    public double p95() {
        return quantile(0.95);
    }

    public double p99() {
        return quantile(0.99);
    }

    public double p999() {
        return quantile(0.999);
    }

    /**
     * Estimates the empirical cumulative distribution function {@code P(X <= x)} in {@code [0.0, 1.0]}.
     * Returns {@link Double#NaN} if the digest is empty.
     */
    public synchronized double cdf(double x) {
        if (!Double.isFinite(x)) {
            throw new IllegalArgumentException("x must be finite: " + x);
        }
        compressLocked();
        if (mergedCount == 0) {
            return Double.NaN;
        }
        if (x < min) {
            return 0.0;
        }
        if (x >= max) {
            return 1.0;
        }
        if (mergedCount == 1) {
            return x < mergedMeans[0] ? 0.0 : 1.0;
        }

        double total = mergedWeight;

        // Between min and first centroid
        if (x < mergedMeans[0]) {
            double span = mergedMeans[0] - min;
            if (span <= 0.0) {
                return (mergedWeights[0] / 2.0) / total;
            }
            return ((x - min) / span) * (mergedWeights[0] / 2.0) / total;
        }

        // Between last centroid and max
        int last = mergedCount - 1;
        if (x >= mergedMeans[last]) {
            double span = max - mergedMeans[last];
            if (span <= 0.0) {
                return 1.0;
            }
            double base = total - mergedWeights[last] / 2.0;
            return (base + ((x - mergedMeans[last]) / span) * (mergedWeights[last] / 2.0)) / total;
        }

        // Interior
        double weightSoFar = mergedWeights[0] / 2.0;
        for (int i = 0; i < last; i++) {
            double leftMean = mergedMeans[i];
            double rightMean = mergedMeans[i + 1];
            double dw = (mergedWeights[i] + mergedWeights[i + 1]) / 2.0;
            if (x < rightMean) {
                double span = rightMean - leftMean;
                if (span <= 0.0) {
                    return (weightSoFar + dw) / total;
                }
                return (weightSoFar + dw * (x - leftMean) / span) / total;
            }
            weightSoFar += dw;
        }

        return 1.0;
    }

    /**
     * Returns the minimum observed value, or {@link Double#NaN} if empty.
     */
    public synchronized double min() {
        return countLocked() == 0.0 ? Double.NaN : min;
    }

    /**
     * Returns the maximum observed value, or {@link Double#NaN} if empty.
     */
    public synchronized double max() {
        return countLocked() == 0.0 ? Double.NaN : max;
    }

    /**
     * Returns the total accumulated weight (equal to sample count when using unit weights).
     */
    public synchronized double count() {
        return countLocked();
    }

    /**
     * Returns the configured compression factor {@code delta}.
     */
    public double compression() {
        return compression;
    }

    /**
     * Returns the current number of merged centroids after compression.
     */
    public synchronized int centroidCount() {
        compressLocked();
        return mergedCount;
    }

    /**
     * Returns an immutable ascending-sorted snapshot of all centroids.
     */
    public synchronized List<Centroid> centroids() {
        compressLocked();
        List<Centroid> list = new ArrayList<>(mergedCount);
        for (int i = 0; i < mergedCount; i++) {
            list.add(new Centroid(mergedMeans[i], mergedWeights[i]));
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Serializes this digest into a compact big-endian binary format.
     */
    public synchronized byte[] toBytes() {
        compressLocked();
        int size = 4 + 1 + 8 + 8 + 8 + 4 + (mergedCount * 16);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putInt(BINARY_MAGIC);
        buf.put(BINARY_VERSION);
        buf.putDouble(compression);
        buf.putDouble(min);
        buf.putDouble(max);
        buf.putInt(mergedCount);
        for (int i = 0; i < mergedCount; i++) {
            buf.putDouble(mergedMeans[i]);
            buf.putDouble(mergedWeights[i]);
        }
        return buf.array();
    }

    /**
     * Deserializes a {@link TDigest} previously produced by {@link #toBytes()}.
     */
    public static TDigest fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (bytes.length < 33) {
            throw new IllegalArgumentException("Invalid TDigest binary payload (too short): " + bytes.length);
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        int magic = buf.getInt();
        if (magic != BINARY_MAGIC) {
            throw new IllegalArgumentException("Invalid TDigest magic header: 0x" + Integer.toHexString(magic));
        }
        byte version = buf.get();
        if (version != BINARY_VERSION) {
            throw new IllegalArgumentException("Unsupported TDigest binary version: " + version);
        }
        double compression = buf.getDouble();
        double min = buf.getDouble();
        double max = buf.getDouble();
        int count = buf.getInt();
        if (count < 0) {
            throw new IllegalArgumentException("Negative centroid count: " + count);
        }
        int expectedLen = 33 + count * 16;
        if (bytes.length != expectedLen) {
            throw new IllegalArgumentException("Corrupted TDigest binary length: expected " + expectedLen + ", got " + bytes.length);
        }

        TDigest digest = new TDigest(compression);
        if (count > 0) {
            if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
                throw new IllegalArgumentException("Invalid min/max bounds in serialized TDigest");
            }
            digest.min = min;
            digest.max = max;
            digest.ensureMergedCapacity(count);
            double totalW = 0.0;
            for (int i = 0; i < count; i++) {
                double m = buf.getDouble();
                double w = buf.getDouble();
                if (!Double.isFinite(m) || !Double.isFinite(w) || w <= 0.0) {
                    throw new IllegalArgumentException("Invalid centroid at index " + i);
                }
                digest.mergedMeans[i] = m;
                digest.mergedWeights[i] = w;
                totalW += w;
            }
            digest.mergedCount = count;
            digest.mergedWeight = totalW;
        }
        return digest;
    }

    private double countLocked() {
        return mergedWeight + tempWeight;
    }

    private void compressLocked() {
        if (tempCount == 0) {
            return;
        }
        int totalItems = mergedCount + tempCount;
        double[] allMeans = new double[totalItems];
        double[] allWeights = new double[totalItems];
        System.arraycopy(mergedMeans, 0, allMeans, 0, mergedCount);
        System.arraycopy(mergedWeights, 0, allWeights, 0, mergedCount);
        System.arraycopy(tempMeans, 0, allMeans, mergedCount, tempCount);
        System.arraycopy(tempWeights, 0, allWeights, mergedCount, tempCount);

        Integer[] order = new Integer[totalItems];
        for (int i = 0; i < totalItems; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(allMeans[a], allMeans[b]));

        double totalWeight = mergedWeight + tempWeight;
        boolean reverse = reverseNextMerge;
        reverseNextMerge = !reverseNextMerge;

        ensureMergedCapacity(totalItems);
        int outCount = 0;

        int firstIdx = reverse ? order[totalItems - 1] : order[0];
        double curMean = allMeans[firstIdx];
        double curWeight = allWeights[firstIdx];
        double weightSoFar = 0.0;
        double k0 = scaleK(0.0);

        for (int step = 1; step < totalItems; step++) {
            int idx = reverse ? order[totalItems - 1 - step] : order[step];
            double nextMean = allMeans[idx];
            double nextWeight = allWeights[idx];

            double proposedWeight = curWeight + nextWeight;
            double q1 = (weightSoFar + proposedWeight) / totalWeight;
            double k1 = scaleK(q1);

            if (k1 - k0 <= 1.0) {
                curMean = curMean + nextWeight * (nextMean - curMean) / proposedWeight;
                curWeight = proposedWeight;
            } else {
                mergedMeans[outCount] = curMean;
                mergedWeights[outCount] = curWeight;
                outCount++;
                weightSoFar += curWeight;
                k0 = scaleK(weightSoFar / totalWeight);
                curMean = nextMean;
                curWeight = nextWeight;
            }
        }

        mergedMeans[outCount] = curMean;
        mergedWeights[outCount] = curWeight;
        outCount++;

        if (reverse) {
            for (int i = 0, j = outCount - 1; i < j; i++, j--) {
                double tm = mergedMeans[i];
                mergedMeans[i] = mergedMeans[j];
                mergedMeans[j] = tm;
                double tw = mergedWeights[i];
                mergedWeights[i] = mergedWeights[j];
                mergedWeights[j] = tw;
            }
        }

        mergedCount = outCount;
        mergedWeight = totalWeight;
        tempCount = 0;
        tempWeight = 0.0;
    }

    /**
     * Dunning's K_1 scale function: {@code (delta / (2 * pi)) * asin(2q - 1)}.
     */
    private double scaleK(double q) {
        double clamped = Math.max(0.0, Math.min(1.0, q));
        return (compression / (2.0 * Math.PI)) * Math.asin(2.0 * clamped - 1.0);
    }

    private void ensureMergedCapacity(int required) {
        if (mergedMeans.length < required) {
            int newCap = Math.max(required, mergedMeans.length * 2);
            mergedMeans = Arrays.copyOf(mergedMeans, newCap);
            mergedWeights = Arrays.copyOf(mergedWeights, newCap);
        }
    }

    private static double weightedAverage(double x1, double w1, double x2, double w2) {
        if (x1 <= x2) {
            return Math.max(x1, Math.min(x2, (x1 * w1 + x2 * w2) / (w1 + w2)));
        }
        return Math.max(x2, Math.min(x1, (x1 * w1 + x2 * w2) / (w1 + w2)));
    }
}
