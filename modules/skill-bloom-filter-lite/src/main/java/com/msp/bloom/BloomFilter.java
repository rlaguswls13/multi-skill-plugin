package com.msp.bloom;

import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe memory-efficient Bloom filter for probabilistic set membership tests.
 * <p>
 * Uses Kirsch-Mitzenmacher double-hashing based on MurmurHash3 (128-bit) to simulate
 * {@code k} independent hash functions with 0% false negative guarantee.
 *
 * @param <T> element type
 */
public final class BloomFilter<T> {

    private final BitSet bitSet;
    private final int bitSize;
    private final int hashCount;
    private final Funnel<T> funnel;
    private final long expectedInsertions;
    private final double expectedFpp;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private long elementCount = 0;

    private BloomFilter(Funnel<T> funnel, int bitSize, int hashCount, long expectedInsertions, double expectedFpp) {
        this.funnel = Objects.requireNonNull(funnel, "funnel cannot be null");
        if (bitSize <= 0) {
            throw new IllegalArgumentException("bitSize must be positive: " + bitSize);
        }
        if (hashCount <= 0) {
            throw new IllegalArgumentException("hashCount must be positive: " + hashCount);
        }
        this.bitSize = bitSize;
        this.hashCount = hashCount;
        this.expectedInsertions = expectedInsertions;
        this.expectedFpp = expectedFpp;
        this.bitSet = new BitSet(bitSize);
    }

    /**
     * Creates a Bloom filter configured for the given expected insertion count and target false positive rate.
     *
     * @param funnel             serializer for elements of type T
     * @param expectedInsertions expected number of elements to insert (> 0)
     * @param falsePositiveRate  desired false positive probability (0.0 < p < 1.0)
     */
    public static <T> BloomFilter<T> create(Funnel<T> funnel, long expectedInsertions, double falsePositiveRate) {
        if (expectedInsertions <= 0) {
            throw new IllegalArgumentException("expectedInsertions must be positive: " + expectedInsertions);
        }
        if (falsePositiveRate <= 0.0 || falsePositiveRate >= 1.0 || Double.isNaN(falsePositiveRate)) {
            throw new IllegalArgumentException("falsePositiveRate must be in range (0.0, 1.0): " + falsePositiveRate);
        }
        int numBits = optimalBitSize(expectedInsertions, falsePositiveRate);
        int numHashes = optimalHashCount(expectedInsertions, numBits);
        return new BloomFilter<>(funnel, numBits, numHashes, expectedInsertions, falsePositiveRate);
    }

    /**
     * Creates a Bloom filter with explicit bit size and hash count.
     */
    public static <T> BloomFilter<T> create(Funnel<T> funnel, int bitSize, int hashCount) {
        return new BloomFilter<>(funnel, bitSize, hashCount, -1, -1.0);
    }

    /**
     * Calculates optimal bit size {@code m} for {@code n} elements and false positive rate {@code p}:
     * m = - (n * ln(p)) / (ln(2)^2).
     */
    public static int optimalBitSize(long n, double p) {
        if (n <= 0) {
            throw new IllegalArgumentException("Expected insertions must be positive: " + n);
        }
        if (p <= 0.0 || p >= 1.0 || Double.isNaN(p)) {
            throw new IllegalArgumentException("False positive rate must be between 0.0 and 1.0: " + p);
        }
        double m = -n * Math.log(p) / (Math.log(2) * Math.log(2));
        if (m > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Requested capacity exceeds BitSet maximum size (Integer.MAX_VALUE bits)");
        }
        return Math.max(1, (int) Math.ceil(m));
    }

    /**
     * Calculates optimal hash function count {@code k} for {@code n} elements and {@code m} bits:
     * k = (m / n) * ln(2).
     */
    public static int optimalHashCount(long n, long m) {
        if (n <= 0 || m <= 0) {
            throw new IllegalArgumentException("n and m must be positive");
        }
        double k = ((double) m / n) * Math.log(2);
        return Math.max(1, (int) Math.round(k));
    }

    /**
     * Inserts an element into the Bloom filter.
     *
     * @param element item to add
     * @return true if at least one bit was newly set (filter state changed), false if bits were already set
     */
    public boolean put(T element) {
        long[] hashes = hashElement(element);
        long h1 = hashes[0];
        long h2 = hashes[1];

        boolean bitsChanged = false;
        rwLock.writeLock().lock();
        try {
            for (int i = 0; i < hashCount; i++) {
                long combinedHash = h1 + i * h2;
                int bitIndex = (int) ((combinedHash & Long.MAX_VALUE) % bitSize);
                if (!bitSet.get(bitIndex)) {
                    bitSet.set(bitIndex);
                    bitsChanged = true;
                }
            }
            elementCount++;
            return bitsChanged;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Tests whether an element might have been added to the Bloom filter.
     * <p>
     * - If this returns {@code false}, the element is definitely NOT in the filter (0% false negatives).
     * - If this returns {@code true}, the element is PROBABLY in the filter (subject to false positive rate).
     *
     * @param element item to check
     * @return false if definitely absent, true if probably present
     */
    public boolean mightContain(T element) {
        long[] hashes = hashElement(element);
        long h1 = hashes[0];
        long h2 = hashes[1];

        rwLock.readLock().lock();
        try {
            for (int i = 0; i < hashCount; i++) {
                long combinedHash = h1 + i * h2;
                int bitIndex = (int) ((combinedHash & Long.MAX_VALUE) % bitSize);
                if (!bitSet.get(bitIndex)) {
                    return false;
                }
            }
            return true;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Current empirical false positive probability based on the fraction of set bits:
     * (bitsSet / totalBits)^k.
     */
    public double falsePositiveProbability() {
        rwLock.readLock().lock();
        try {
            if (elementCount == 0) {
                return 0.0;
            }
            double setRatio = (double) bitSet.cardinality() / bitSize;
            return Math.pow(setRatio, hashCount);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Total number of bits allocated in the underlying bit vector.
     */
    public int bitCount() {
        return bitSize;
    }

    /** Alias for {@link #bitCount()} */
    public int sizeInBits() {
        return bitSize;
    }

    /**
     * Number of bits currently set to 1.
     */
    public int bitsSetCount() {
        rwLock.readLock().lock();
        try {
            return bitSet.cardinality();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Number of elements added via {@link #put(Object)}.
     */
    public long elementCount() {
        rwLock.readLock().lock();
        try {
            return elementCount;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /** Number of simulated hash functions. */
    public int hashCount() {
        return hashCount;
    }

    /** Expected insertion count configured during creation, or -1 if custom sizing was used. */
    public long expectedInsertions() {
        return expectedInsertions;
    }

    /** Expected false positive rate configured during creation, or -1.0 if custom sizing was used. */
    public double expectedFalsePositiveRate() {
        return expectedFpp;
    }

    /** Clears all bits and resets the element count. */
    public void reset() {
        rwLock.writeLock().lock();
        try {
            bitSet.clear();
            elementCount = 0;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private long[] hashElement(T element) {
        ByteArraySink sink = new ByteArraySink();
        funnel.funnel(element, sink);
        byte[] bytes = sink.toByteArray();
        return Murmur3.hash128(bytes, 0, bytes.length, 0x9747b28c);
    }
}
