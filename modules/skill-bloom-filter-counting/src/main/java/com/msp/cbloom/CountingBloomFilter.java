package com.msp.cbloom;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Counting Bloom Filter that supports addition, deletion, and approximate frequency estimation.
 * Uses 8-bit unsigned counters (0..255) per bucket to eliminate counter overflow up to 255.
 * Thread-safe using read-write locks.
 */
public class CountingBloomFilter {

    private static final int DEFAULT_CAPACITY = 2048;
    private static final int DEFAULT_HASH_COUNT = 4;

    private final int capacity;
    private final int hashCount;
    private final byte[] counters;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Constructs a CountingBloomFilter with explicit bucket capacity and hash function count.
     *
     * @param capacity  number of counter buckets (must be > 0)
     * @param hashCount number of hash functions to apply (must be > 0)
     */
    public CountingBloomFilter(int capacity, int hashCount) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        if (hashCount <= 0) {
            throw new IllegalArgumentException("Hash count must be positive: " + hashCount);
        }
        this.capacity = capacity;
        this.hashCount = hashCount;
        this.counters = new byte[capacity];
    }

    /**
     * Constructs a default CountingBloomFilter (2048 buckets, 4 hash functions).
     */
    public CountingBloomFilter() {
        this(DEFAULT_CAPACITY, DEFAULT_HASH_COUNT);
    }

    /**
     * Creates an optimally sized CountingBloomFilter based on expected insertions and false positive probability.
     *
     * @param expectedInsertions expected number of unique items to store
     * @param fpp                target false positive probability (0.0 < fpp < 1.0)
     * @return a new CountingBloomFilter instance
     */
    public static CountingBloomFilter create(int expectedInsertions, double fpp) {
        if (expectedInsertions <= 0) {
            throw new IllegalArgumentException("Expected insertions must be > 0");
        }
        if (fpp <= 0.0 || fpp >= 1.0) {
            throw new IllegalArgumentException("False positive probability must be in (0, 1): " + fpp);
        }

        // Optimal bucket size m = -n * ln(p) / (ln 2)^2
        long m = (long) Math.ceil(-expectedInsertions * Math.log(fpp) / (Math.log(2) * Math.log(2)));
        int optimalCapacity = (int) Math.min(Integer.MAX_VALUE - 8, Math.max(16, m));

        // Optimal hash count k = (m / n) * ln 2
        int optimalK = Math.max(1, (int) Math.round(((double) optimalCapacity / expectedInsertions) * Math.log(2)));

        return new CountingBloomFilter(optimalCapacity, optimalK);
    }

    /**
     * Adds an element into the counting filter, incrementing the corresponding bucket counters.
     *
     * @param element the string element to add
     */
    public void add(String element) {
        Objects.requireNonNull(element, "element must not be null");
        int[] indices = computeIndices(element);

        lock.writeLock().lock();
        try {
            for (int idx : indices) {
                int current = counters[idx] & 0xFF;
                if (current < 255) {
                    counters[idx] = (byte) (current + 1);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes an element from the counting filter, decrementing the corresponding bucket counters.
     *
     * @param element the string element to remove
     * @return true if the element might have been present and counters were decremented; false otherwise
     */
    public boolean remove(String element) {
        Objects.requireNonNull(element, "element must not be null");
        int[] indices = computeIndices(element);

        lock.writeLock().lock();
        try {
            for (int idx : indices) {
                if ((counters[idx] & 0xFF) == 0) {
                    return false;
                }
            }
            for (int idx : indices) {
                int current = counters[idx] & 0xFF;
                if (current > 0) {
                    counters[idx] = (byte) (current - 1);
                }
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Tests if an element might be present in the filter.
     *
     * @param element the string element to query
     * @return true if all bucket counters for the element are greater than 0; false guarantees absence
     */
    public boolean mightContain(String element) {
        Objects.requireNonNull(element, "element must not be null");
        int[] indices = computeIndices(element);

        lock.readLock().lock();
        try {
            for (int idx : indices) {
                if ((counters[idx] & 0xFF) == 0) {
                    return false;
                }
            }
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Estimates the approximate insertion count of the element (minimum of its bucket counters).
     *
     * @param element the string element to query
     * @return approximate count (0 if not present)
     */
    public int approximateCount(String element) {
        Objects.requireNonNull(element, "element must not be null");
        int[] indices = computeIndices(element);

        lock.readLock().lock();
        try {
            int min = Integer.MAX_VALUE;
            for (int idx : indices) {
                int count = counters[idx] & 0xFF;
                if (count < min) {
                    min = count;
                }
            }
            return min == Integer.MAX_VALUE ? 0 : min;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Resets all counters to 0.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            Arrays.fill(counters, (byte) 0);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the total number of counter buckets.
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns the number of hash functions used per element.
     */
    public int hashCount() {
        return hashCount;
    }

    /**
     * Generates k bucket indices for the given element using SHA-256 Kirsch-Mitzenmacher double-hashing.
     */
    private int[] computeIndices(String element) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(element.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.wrap(digest);
            long h1 = buffer.getLong();
            long h2 = buffer.getLong();
            if (h2 == 0) {
                h2 = 1;
            }

            int[] indices = new int[hashCount];
            for (int i = 0; i < hashCount; i++) {
                long combined = h1 + (long) i * h2;
                indices[i] = (int) Math.floorMod(combined, capacity);
            }
            return indices;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm must be available in standard JVM", e);
        }
    }
}
