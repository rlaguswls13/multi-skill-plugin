package com.msp.scalablebloom;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Scalable Bloom Filter (Almeida et al. 2007) implementation in pure Java 21.
 * <p>
 * Dynamically adds new Bloom filter layers with geometric capacity scaling and tightened
 * error rates as capacity fills, bounding the overall compound false positive probability:
 * <pre>
 *   P_total &lt;= sum_{i=0}^inf (P_i) = P_0 / (1 - r) == targetFpr
 * </pre>
 *
 * @param <T> element type
 */
public final class ScalableBloomFilter<T> {

    public static final double DEFAULT_TIGHTENING_RATIO = 0.85;
    public static final int DEFAULT_GROWTH_FACTOR = 2;

    private final double targetFpr;
    private final double tighteningRatio;
    private final int growthFactor;
    private final int initialCapacity;

    private final List<BloomLayer> layers = new ArrayList<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private long totalElementsAdded = 0;

    /**
     * Creates a scalable Bloom filter with default tightening ratio (0.85) and growth factor (2).
     *
     * @param initialCapacity   initial capacity of the first layer
     * @param falsePositiveRate desired overall false positive probability (e.g. 0.01 for 1%)
     */
    public ScalableBloomFilter(int initialCapacity, double falsePositiveRate) {
        this(initialCapacity, falsePositiveRate, DEFAULT_TIGHTENING_RATIO, DEFAULT_GROWTH_FACTOR);
    }

    /**
     * Creates a scalable Bloom filter with custom configuration.
     *
     * @param initialCapacity   initial capacity of the first layer (&gt; 0)
     * @param falsePositiveRate target false positive rate in (0.0, 1.0)
     * @param tighteningRatio   error rate tightening factor for each successive layer in (0.0, 1.0)
     * @param growthFactor      capacity multiplier for each successive layer (&gt;= 2)
     */
    public ScalableBloomFilter(int initialCapacity, double falsePositiveRate, double tighteningRatio, int growthFactor) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be positive: " + initialCapacity);
        }
        if (falsePositiveRate <= 0.0 || falsePositiveRate >= 1.0) {
            throw new IllegalArgumentException("falsePositiveRate must be strictly between 0 and 1: " + falsePositiveRate);
        }
        if (tighteningRatio <= 0.0 || tighteningRatio >= 1.0) {
            throw new IllegalArgumentException("tighteningRatio must be strictly between 0 and 1: " + tighteningRatio);
        }
        if (growthFactor < 2) {
            throw new IllegalArgumentException("growthFactor must be at least 2: " + growthFactor);
        }

        this.initialCapacity = initialCapacity;
        this.targetFpr = falsePositiveRate;
        this.tighteningRatio = tighteningRatio;
        this.growthFactor = growthFactor;

        // P0 = P * (1 - r) ensures sum_{i=0}^inf (P0 * r^i) = P
        double p0 = falsePositiveRate * (1.0 - tighteningRatio);
        this.layers.add(new BloomLayer(initialCapacity, p0));
    }

    /**
     * Adds an item to the filter. Dynamically adds a new layer if the active layer has reached capacity.
     *
     * @param item non-null item to add
     */
    public void add(T item) {
        Objects.requireNonNull(item, "Item to add must not be null");
        rwLock.writeLock().lock();
        try {
            BloomLayer activeLayer = layers.getLast();
            if (activeLayer.isFull()) {
                int nextIndex = layers.size();
                long nextCapacityLong = (long) initialCapacity * (long) Math.pow(growthFactor, nextIndex);
                int nextCapacity = (int) Math.min(Integer.MAX_VALUE - 8, nextCapacityLong);
                double nextFpr = targetFpr * (1.0 - tighteningRatio) * Math.pow(tighteningRatio, nextIndex);

                activeLayer = new BloomLayer(nextCapacity, nextFpr);
                layers.add(activeLayer);
            }

            activeLayer.add(item);
            totalElementsAdded++;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Tests whether the item might be in the filter.
     *
     * @param item non-null item to check
     * @return true if the item might have been added; false if it definitely has not
     */
    public boolean contains(T item) {
        Objects.requireNonNull(item, "Item to check must not be null");
        rwLock.readLock().lock();
        try {
            // Check layers in reverse (most recent layer first)
            for (int i = layers.size() - 1; i >= 0; i--) {
                if (layers.get(i).contains(item)) {
                    return true;
                }
            }
            return false;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the total capacity summed across all currently instantiated layers.
     */
    public int totalCapacity() {
        rwLock.readLock().lock();
        try {
            long sum = 0;
            for (BloomLayer layer : layers) {
                sum += layer.capacity;
            }
            return (int) Math.min(Integer.MAX_VALUE, sum);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the number of currently active layers.
     */
    public int layerCount() {
        rwLock.readLock().lock();
        try {
            return layers.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the approximate total number of elements added to the filter.
     */
    public long approxElementCount() {
        rwLock.readLock().lock();
        try {
            return totalElementsAdded;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Internal standard Bloom filter layer.
     */
    private static final class BloomLayer {
        private final int capacity;
        private final int bitSize;
        private final int numHashes;
        private final BitSet bits;
        private int count = 0;

        BloomLayer(int capacity, double fpr) {
            this.capacity = capacity;
            // m = - (n * ln(p)) / (ln(2)^2)
            double ln2 = Math.log(2.0);
            double optimalBits = -((double) capacity * Math.log(fpr)) / (ln2 * ln2);
            int m = (int) Math.max(64, Math.min(Integer.MAX_VALUE - 8, Math.ceil(optimalBits)));
            // k = (m / n) * ln(2)
            int k = (int) Math.max(1, Math.min(30, Math.round(((double) m / capacity) * ln2)));

            this.bitSize = m;
            this.numHashes = k;
            this.bits = new BitSet(bitSize);
        }

        boolean isFull() {
            return count >= capacity;
        }

        <E> void add(E item) {
            long[] hashes = hashItem(item);
            long h1 = hashes[0];
            long h2 = hashes[1];

            for (int i = 0; i < numHashes; i++) {
                long combined = h1 + (long) i * h2;
                int bitIndex = Math.floorMod(combined, bitSize);
                bits.set(bitIndex);
            }
            count++;
        }

        <E> boolean contains(E item) {
            long[] hashes = hashItem(item);
            long h1 = hashes[0];
            long h2 = hashes[1];

            for (int i = 0; i < numHashes; i++) {
                long combined = h1 + (long) i * h2;
                int bitIndex = Math.floorMod(combined, bitSize);
                if (!bits.get(bitIndex)) {
                    return false;
                }
            }
            return true;
        }

        private static <E> long[] hashItem(E item) {
            byte[] bytes = String.valueOf(item).getBytes(StandardCharsets.UTF_8);
            return murmur3_128(bytes, 0, bytes.length, 0x9747b28c);
        }

        private static long[] murmur3_128(byte[] data, int offset, int length, int seed) {
            long h1 = seed & 0xFFFFFFFFL;
            long h2 = seed & 0xFFFFFFFFL;

            long c1 = 0x87c37b91114253d5L;
            long c2 = 0x4cf5ad432745937fL;

            int nblocks = length / 16;

            for (int i = 0; i < nblocks; i++) {
                int idx = offset + i * 16;
                long k1 = getLittleEndianLong(data, idx);
                long k2 = getLittleEndianLong(data, idx + 8);

                k1 *= c1;
                k1 = Long.rotateLeft(k1, 31);
                k1 *= c2;
                h1 ^= k1;

                h1 = Long.rotateLeft(h1, 27);
                h1 += h2;
                h1 = h1 * 5 + 0x52dce729L;

                k2 *= c2;
                k2 = Long.rotateLeft(k2, 33);
                k2 *= c1;
                h2 ^= k2;

                h2 = Long.rotateLeft(h2, 31);
                h2 += h1;
                h2 = h2 * 5 + 0x38495ab5L;
            }

            // tail
            int tailOffset = offset + nblocks * 16;
            long k1 = 0;
            long k2 = 0;

            switch (length & 15) {
                case 15: k2 ^= ((long) data[tailOffset + 14] & 0xFF) << 48;
                case 14: k2 ^= ((long) data[tailOffset + 13] & 0xFF) << 40;
                case 13: k2 ^= ((long) data[tailOffset + 12] & 0xFF) << 32;
                case 12: k2 ^= ((long) data[tailOffset + 11] & 0xFF) << 24;
                case 11: k2 ^= ((long) data[tailOffset + 10] & 0xFF) << 16;
                case 10: k2 ^= ((long) data[tailOffset + 9] & 0xFF) << 8;
                case 9:  k2 ^= ((long) data[tailOffset + 8] & 0xFF);
                         k2 *= c2; k2 = Long.rotateLeft(k2, 33); k2 *= c1; h2 ^= k2;
                case 8:  k1 ^= ((long) data[tailOffset + 7] & 0xFF) << 56;
                case 7:  k1 ^= ((long) data[tailOffset + 6] & 0xFF) << 48;
                case 6:  k1 ^= ((long) data[tailOffset + 5] & 0xFF) << 40;
                case 5:  k1 ^= ((long) data[tailOffset + 4] & 0xFF) << 32;
                case 4:  k1 ^= ((long) data[tailOffset + 3] & 0xFF) << 24;
                case 3:  k1 ^= ((long) data[tailOffset + 2] & 0xFF) << 16;
                case 2:  k1 ^= ((long) data[tailOffset + 1] & 0xFF) << 8;
                case 1:  k1 ^= ((long) data[tailOffset] & 0xFF);
                         k1 *= c1; k1 = Long.rotateLeft(k1, 31); k1 *= c2; h1 ^= k1;
            }

            h1 ^= length;
            h2 ^= length;

            h1 += h2;
            h2 += h1;

            h1 = fmix64(h1);
            h2 = fmix64(h2);

            h1 += h2;
            h2 += h1;

            return new long[]{h1, h2};
        }

        private static long getLittleEndianLong(byte[] data, int offset) {
            return (((long) data[offset] & 0xFF))
                    | (((long) data[offset + 1] & 0xFF) << 8)
                    | (((long) data[offset + 2] & 0xFF) << 16)
                    | (((long) data[offset + 3] & 0xFF) << 24)
                    | (((long) data[offset + 4] & 0xFF) << 32)
                    | (((long) data[offset + 5] & 0xFF) << 40)
                    | (((long) data[offset + 6] & 0xFF) << 48)
                    | (((long) data[offset + 7] & 0xFF) << 56);
        }

        private static long fmix64(long k) {
            k ^= k >>> 33;
            k *= 0xff51afd7ed558ccdL;
            k ^= k >>> 33;
            k *= 0xc4ceb9fe1a85ec53L;
            k ^= k >>> 33;
            return k;
        }
    }
}
