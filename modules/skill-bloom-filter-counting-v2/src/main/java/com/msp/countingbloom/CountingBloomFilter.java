package com.msp.countingbloom;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe Counting Bloom Filter supporting 4-bit and 8-bit saturating counters.
 * Supports adding items, removing items, testing membership, and counter saturation to prevent underflow.
 *
 * @param <T> Element type
 */
public class CountingBloomFilter<T> {

    private final int m; // Number of counters
    private final int k; // Number of hash functions
    private final int counterBits; // 4 or 8
    private final int maxCounterValue; // 15 for 4-bit, 255 for 8-bit
    private final byte[] counters;
    private int elementCount;
    private final ReentrantLock lock = new ReentrantLock();

    private CountingBloomFilter(int m, int k, int counterBits) {
        this.m = m;
        this.k = k;
        this.counterBits = counterBits;
        this.maxCounterValue = (counterBits == 4) ? 15 : 255;
        int byteSize = (counterBits == 4) ? ((m + 1) / 2) : m;
        this.counters = new byte[byteSize];
        this.elementCount = 0;
    }

    /**
     * Creates a CountingBloomFilter with calculated optimal parameters.
     *
     * @param expectedInsertions estimated number of distinct items to store (> 0)
     * @param fpp                desired false positive probability (0.0 < fpp < 1.0)
     * @param counterBits        counter width: must be 4 or 8
     * @return initialized CountingBloomFilter
     */
    public static <T> CountingBloomFilter<T> create(int expectedInsertions, double fpp, int counterBits) {
        if (expectedInsertions <= 0) {
            throw new IllegalArgumentException("Expected insertions must be positive: " + expectedInsertions);
        }
        if (fpp <= 0.0 || fpp >= 1.0) {
            throw new IllegalArgumentException("False positive probability must be in (0, 1): " + fpp);
        }
        if (counterBits != 4 && counterBits != 8) {
            throw new IllegalArgumentException("Counter bits must be 4 or 8, got: " + counterBits);
        }

        // m = ceil(-n * ln(p) / (ln(2)^2))
        double ln2 = Math.log(2);
        int m = (int) Math.ceil(-expectedInsertions * Math.log(fpp) / (ln2 * ln2));
        if (m < 8) {
            m = 8;
        }

        // k = round((m / n) * ln(2))
        int k = Math.max(1, (int) Math.round(((double) m / expectedInsertions) * ln2));

        return new CountingBloomFilter<>(m, k, counterBits);
    }

    /**
     * Adds an element into the filter by incrementing its hash position counters.
     * Counters saturate at their maximum value (15 or 255) to prevent overflow.
     *
     * @param item element to add
     * @return true
     */
    public boolean add(T item) {
        Objects.requireNonNull(item, "Item to add cannot be null");
        byte[] bytes = toBytes(item);
        int[] indices = hashIndices(bytes);

        lock.lock();
        try {
            for (int idx : indices) {
                int val = getCounter(idx);
                if (val < maxCounterValue) {
                    setCounter(idx, val + 1);
                }
            }
            elementCount++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes an element from the filter if it might be contained.
     * Non-saturated counters are decremented. Saturated counters remain intact to prevent false negatives.
     *
     * @param item element to remove
     * @return true if item was potentially present and removed, false if definitely not present
     */
    public boolean remove(T item) {
        Objects.requireNonNull(item, "Item to remove cannot be null");
        byte[] bytes = toBytes(item);
        int[] indices = hashIndices(bytes);

        lock.lock();
        try {
            // First check if all counters are > 0
            for (int idx : indices) {
                if (getCounter(idx) == 0) {
                    return false;
                }
            }

            // Decrement non-saturated counters
            for (int idx : indices) {
                int val = getCounter(idx);
                if (val > 0 && val < maxCounterValue) {
                    setCounter(idx, val - 1);
                }
            }

            if (elementCount > 0) {
                elementCount--;
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Tests whether an element might have been added to the filter.
     *
     * @param item element to test
     * @return true if possibly present, false if definitely absent
     */
    public boolean mightContain(T item) {
        Objects.requireNonNull(item, "Item to test cannot be null");
        byte[] bytes = toBytes(item);
        int[] indices = hashIndices(bytes);

        lock.lock();
        try {
            for (int idx : indices) {
                if (getCounter(idx) == 0) {
                    return false;
                }
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the approximate number of currently active elements.
     */
    public int approximateElementCount() {
        lock.lock();
        try {
            return elementCount;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets all counters and clears the filter.
     */
    public void clear() {
        lock.lock();
        try {
            Arrays.fill(counters, (byte) 0);
            elementCount = 0;
        } finally {
            lock.unlock();
        }
    }

    public int getM() {
        return m;
    }

    public int getK() {
        return k;
    }

    public int getCounterBits() {
        return counterBits;
    }

    private int getCounter(int index) {
        if (counterBits == 8) {
            return counters[index] & 0xFF;
        } else {
            // 4-bit: high nibble for even index, low nibble for odd index
            int byteIndex = index / 2;
            int b = counters[byteIndex] & 0xFF;
            return ((index & 1) == 0) ? (b >>> 4) : (b & 0x0F);
        }
    }

    private void setCounter(int index, int val) {
        if (counterBits == 8) {
            counters[index] = (byte) val;
        } else {
            int byteIndex = index / 2;
            int b = counters[byteIndex] & 0xFF;
            if ((index & 1) == 0) {
                counters[byteIndex] = (byte) ((b & 0x0F) | ((val & 0x0F) << 4));
            } else {
                counters[byteIndex] = (byte) ((b & 0xF0) | (val & 0x0F));
            }
        }
    }

    private int[] hashIndices(byte[] bytes) {
        int[] indices = new int[k];
        long h1 = Murmur3.hash64(bytes, 0x12345678);
        long h2 = Murmur3.hash64(bytes, 0x9ABCDEF0);
        if (h2 == 0) {
            h2 = 1;
        }

        for (int i = 0; i < k; i++) {
            long combined = h1 + (long) i * h2;
            int idx = (int) Math.floorMod(combined, (long) m);
            indices[i] = idx;
        }
        return indices;
    }

    private static byte[] toBytes(Object obj) {
        if (obj instanceof byte[] b) {
            return b;
        }
        if (obj instanceof CharSequence cs) {
            return cs.toString().getBytes(StandardCharsets.UTF_8);
        }
        return obj.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Lightweight 64-bit MurmurHash3 implementation.
     */
    private static final class Murmur3 {
        private static final long C1 = 0x87c37b91114253d5L;
        private static final long C2 = 0x4cf5ad432745937fL;

        static long hash64(byte[] data, int seed) {
            long h = seed & 0xFFFFFFFFL;
            int length = data.length;
            int nblocks = length / 8;

            for (int i = 0; i < nblocks; i++) {
                int idx = i * 8;
                long k = ((long) data[idx] & 0xff)
                        | (((long) data[idx + 1] & 0xff) << 8)
                        | (((long) data[idx + 2] & 0xff) << 16)
                        | (((long) data[idx + 3] & 0xff) << 24)
                        | (((long) data[idx + 4] & 0xff) << 32)
                        | (((long) data[idx + 5] & 0xff) << 40)
                        | (((long) data[idx + 6] & 0xff) << 48)
                        | (((long) data[idx + 7] & 0xff) << 56);

                k *= C1;
                k = Long.rotateLeft(k, 31);
                k *= C2;

                h ^= k;
                h = Long.rotateLeft(h, 27);
                h = h * 5 + 0x52dce729L;
            }

            int tailIdx = nblocks * 8;
            long k1 = 0;
            switch (length & 7) {
                case 7:
                    k1 ^= ((long) data[tailIdx + 6] & 0xff) << 48;
                case 6:
                    k1 ^= ((long) data[tailIdx + 5] & 0xff) << 40;
                case 5:
                    k1 ^= ((long) data[tailIdx + 4] & 0xff) << 32;
                case 4:
                    k1 ^= ((long) data[tailIdx + 3] & 0xff) << 24;
                case 3:
                    k1 ^= ((long) data[tailIdx + 2] & 0xff) << 16;
                case 2:
                    k1 ^= ((long) data[tailIdx + 1] & 0xff) << 8;
                case 1:
                    k1 ^= ((long) data[tailIdx] & 0xff);
                    k1 *= C1;
                    k1 = Long.rotateLeft(k1, 31);
                    k1 *= C2;
                    h ^= k1;
                default:
                    break;
            }

            h ^= length;
            h ^= (h >>> 33);
            h *= 0xff51afd7ed558ccdL;
            h ^= (h >>> 33);
            h *= 0xc4ceb9fe1a85ec53L;
            h ^= (h >>> 33);
            return h;
        }
    }
}
