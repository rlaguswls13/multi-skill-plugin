package com.msp.partbloom;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-performance Partitioned Bloom Filter where each hash function maps to an independent bit slice (M / k bits).
 * <p>
 * Benefits of partitioning:
 * <ul>
 *     <li>Optimizes CPU cache locality (each hash probe addresses a predictable localized slice)</li>
 *     <li>Eliminates hash collision bias across slices</li>
 *     <li>Uses Kirsch-Mitzenmacher dual hashing: {@code h(i) = (h1 + i * h2) % sliceBits}</li>
 * </ul>
 *
 * @param <T> element type
 */
public class PartitionedBloomFilter<T> {

    private final int sliceBits;
    private final int hashCount;
    private final int totalBits;
    private final long[] bitWords;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private int insertions = 0;

    /**
     * Internal constructor with pre-validated dimensions.
     */
    private PartitionedBloomFilter(int sliceBits, int hashCount) {
        this.sliceBits = sliceBits;
        this.hashCount = hashCount;
        this.totalBits = sliceBits * hashCount;
        int wordCount = (int) (((long) totalBits + 63) / 64);
        this.bitWords = new long[wordCount];
    }

    /**
     * Creates a Partitioned Bloom Filter calibrated for an expected number of insertions and target false positive probability.
     *
     * @param expectedInsertions expected number of elements (must be positive)
     * @param fpp                target false positive probability (0.0 < fpp < 1.0)
     * @param <T>                element type
     * @return configured PartitionedBloomFilter
     */
    public static <T> PartitionedBloomFilter<T> create(int expectedInsertions, double fpp) {
        if (expectedInsertions <= 0) {
            throw new IllegalArgumentException("expectedInsertions must be positive: " + expectedInsertions);
        }
        if (fpp <= 0.0 || fpp >= 1.0 || Double.isNaN(fpp)) {
            throw new IllegalArgumentException("fpp must be between 0.0 and 1.0 (exclusive): " + fpp);
        }

        // Optimal number of slices (hashes): k = ceil(-log2(fpp))
        int k = (int) Math.ceil(-Math.log(fpp) / Math.log(2.0));
        k = Math.max(1, k);

        // Optimal slice bits: s = ceil(-n / ln(1 - fpp^(1/k)))
        double pPerSlice = Math.pow(fpp, 1.0 / k);
        int s = (int) Math.ceil(-expectedInsertions / Math.log(1.0 - pPerSlice));
        s = Math.max(1, s);

        return new PartitionedBloomFilter<>(s, k);
    }

    /**
     * Creates a Partitioned Bloom Filter with explicit slice bits and hash count.
     *
     * @param sliceBits number of bits per slice (must be positive)
     * @param hashCount number of slices / hash functions (must be positive)
     * @param <T>       element type
     * @return configured PartitionedBloomFilter
     */
    public static <T> PartitionedBloomFilter<T> create(int sliceBits, int hashCount) {
        if (sliceBits <= 0) {
            throw new IllegalArgumentException("sliceBits must be positive: " + sliceBits);
        }
        if (hashCount <= 0) {
            throw new IllegalArgumentException("hashCount must be positive: " + hashCount);
        }
        return new PartitionedBloomFilter<>(sliceBits, hashCount);
    }

    /**
     * Adds an item to the bloom filter by setting one bit in each partition slice.
     *
     * @param item the item to add
     */
    public void add(T item) {
        long[] hashes = hashItem(item);
        long h1 = hashes[0];
        long h2 = hashes[1];

        writeLock.lock();
        try {
            for (int i = 0; i < hashCount; i++) {
                long combined = h1 + (long) i * h2;
                int bitInSlice = (int) Math.floorMod(combined, (long) sliceBits);
                long globalBitIndex = (long) i * sliceBits + bitInSlice;

                int wordIdx = (int) (globalBitIndex >>> 6);
                long bitMask = 1L << (globalBitIndex & 63L);
                bitWords[wordIdx] |= bitMask;
            }
            insertions++;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Checks if the item might have been added to the bloom filter.
     *
     * @param item the item to query
     * @return true if the item might be in the filter, false if it is definitely not
     */
    public boolean mightContain(T item) {
        long[] hashes = hashItem(item);
        long h1 = hashes[0];
        long h2 = hashes[1];

        readLock.lock();
        try {
            for (int i = 0; i < hashCount; i++) {
                long combined = h1 + (long) i * h2;
                int bitInSlice = (int) Math.floorMod(combined, (long) sliceBits);
                long globalBitIndex = (long) i * sliceBits + bitInSlice;

                int wordIdx = (int) (globalBitIndex >>> 6);
                long bitMask = 1L << (globalBitIndex & 63L);

                if ((bitWords[wordIdx] & bitMask) == 0L) {
                    return false;
                }
            }
            return true;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns the total number of bits across all slices (M = sliceBits * hashCount).
     *
     * @return total bit capacity
     */
    public int totalBits() {
        return totalBits;
    }

    /**
     * Returns the number of bits in each independent slice partition.
     *
     * @return slice bit size
     */
    public int sliceBits() {
        return sliceBits;
    }

    /**
     * Returns the number of slices / hash functions.
     *
     * @return hash count
     */
    public int hashCount() {
        return hashCount;
    }

    /**
     * Returns the theoretical false positive probability estimate based on the current number of insertions.
     *
     * @return current estimated false positive probability
     */
    public double currentFppEstimate() {
        readLock.lock();
        try {
            if (insertions == 0) {
                return 0.0;
            }
            double bitSetProbability = 1.0 - Math.exp(-(double) insertions / sliceBits);
            return Math.pow(bitSetProbability, hashCount);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Clears all set bits and resets the insertion count.
     */
    public void clear() {
        writeLock.lock();
        try {
            Arrays.fill(bitWords, 0L);
            insertions = 0;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns the number of insertions performed so far.
     *
     * @return insertion count
     */
    public int insertionCount() {
        readLock.lock();
        try {
            return insertions;
        } finally {
            readLock.unlock();
        }
    }

    private static long[] hashItem(Object item) {
        byte[] bytes = toBytes(item);
        return murmur3_128(bytes);
    }

    private static byte[] toBytes(Object item) {
        if (item == null) {
            return new byte[]{0};
        }
        if (item instanceof byte[] b) {
            return b;
        }
        if (item instanceof CharSequence cs) {
            return cs.toString().getBytes(StandardCharsets.UTF_8);
        }
        if (item instanceof Integer i) {
            return ByteBuffer.allocate(4).putInt(i).array();
        }
        if (item instanceof Long l) {
            return ByteBuffer.allocate(8).putLong(l).array();
        }
        return item.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Standard MurmurHash3_x64_128 implementation producing two 64-bit independent hashes (h1, h2).
     */
    private static long[] murmur3_128(byte[] data) {
        long h1 = 0L;
        long h2 = 0L;
        long c1 = 0x87c37b91114253d5L;
        long c2 = 0x4cf5ad432745937fL;
        int length = data.length;
        int nblocks = length / 16;

        for (int i = 0; i < nblocks; i++) {
            int index = i * 16;
            long k1 = getLong(data, index);
            long k2 = getLong(data, index + 8);

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

        long k1 = 0;
        long k2 = 0;
        int tailStart = nblocks * 16;
        int rem = length - tailStart;

        switch (rem) {
            case 15: k2 ^= (long) (data[tailStart + 14] & 0xFF) << 48;
            case 14: k2 ^= (long) (data[tailStart + 13] & 0xFF) << 40;
            case 13: k2 ^= (long) (data[tailStart + 12] & 0xFF) << 32;
            case 12: k2 ^= (long) (data[tailStart + 11] & 0xFF) << 24;
            case 11: k2 ^= (long) (data[tailStart + 10] & 0xFF) << 16;
            case 10: k2 ^= (long) (data[tailStart + 9] & 0xFF) << 8;
            case 9:  k2 ^= (long) (data[tailStart + 8] & 0xFF);
                     k2 *= c2;
                     k2 = Long.rotateLeft(k2, 33);
                     k2 *= c1;
                     h2 ^= k2;
            case 8:  k1 ^= (long) (data[tailStart + 7] & 0xFF) << 56;
            case 7:  k1 ^= (long) (data[tailStart + 6] & 0xFF) << 48;
            case 6:  k1 ^= (long) (data[tailStart + 5] & 0xFF) << 40;
            case 5:  k1 ^= (long) (data[tailStart + 4] & 0xFF) << 32;
            case 4:  k1 ^= (long) (data[tailStart + 3] & 0xFF) << 24;
            case 3:  k1 ^= (long) (data[tailStart + 2] & 0xFF) << 16;
            case 2:  k1 ^= (long) (data[tailStart + 1] & 0xFF) << 8;
            case 1:  k1 ^= (long) (data[tailStart] & 0xFF);
                     k1 *= c1;
                     k1 = Long.rotateLeft(k1, 31);
                     k1 *= c2;
                     h1 ^= k1;
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

    private static long getLong(byte[] bytes, int offset) {
        return ((long) bytes[offset] & 0xFF)
                | (((long) bytes[offset + 1] & 0xFF) << 8)
                | (((long) bytes[offset + 2] & 0xFF) << 16)
                | (((long) bytes[offset + 3] & 0xFF) << 24)
                | (((long) bytes[offset + 4] & 0xFF) << 32)
                | (((long) bytes[offset + 5] & 0xFF) << 40)
                | (((long) bytes[offset + 6] & 0xFF) << 48)
                | (((long) bytes[offset + 7] & 0xFF) << 56);
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
