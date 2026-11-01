package com.msp.cuckoo;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

/**
 * High-performance Cuckoo Filter implementation in pure Java 21.
 *
 * <p>Supports insertion, membership query, and deletion with very low false-positive rate.
 * Uses 4 entries per bucket and 16-bit fingerprints.</p>
 *
 * @param <T> type of elements
 */
public class CuckooFilter<T> {

    public static final int ENTRIES_PER_BUCKET = 4;
    public static final int DEFAULT_MAX_KICKS = 500;

    private final Hasher<T> hasher;
    private final int numBuckets;
    private final int bucketMask;
    private final int maxKicks;
    private final short[] table;
    private int count;
    private final Random random;

    public CuckooFilter(Hasher<T> hasher, int expectedCapacity, int maxKicks) {
        if (expectedCapacity <= 0) {
            throw new IllegalArgumentException("expectedCapacity must be positive");
        }
        this.hasher = Objects.requireNonNull(hasher, "hasher cannot be null");
        this.maxKicks = maxKicks > 0 ? maxKicks : DEFAULT_MAX_KICKS;
        this.random = new Random(42L);

        int minBuckets = (expectedCapacity + ENTRIES_PER_BUCKET - 1) / ENTRIES_PER_BUCKET;
        int n = 8;
        while (n < minBuckets) {
            n <<= 1;
        }
        this.numBuckets = n;
        this.bucketMask = n - 1;
        this.table = new short[this.numBuckets * ENTRIES_PER_BUCKET];
        this.count = 0;
    }

    public CuckooFilter(Hasher<T> hasher, int expectedCapacity) {
        this(hasher, expectedCapacity, DEFAULT_MAX_KICKS);
    }

    public static CuckooFilter<String> createForStrings(int expectedCapacity) {
        return new CuckooFilter<>(Hasher.ofString(), expectedCapacity);
    }

    public static <T> CuckooFilter<T> create(Hasher<T> hasher, int expectedCapacity) {
        return new CuckooFilter<>(hasher, expectedCapacity);
    }

    public static <T> CuckooFilter<T> create(int expectedCapacity) {
        return new CuckooFilter<>(Hasher.ofDefault(), expectedCapacity);
    }

    /**
     * Inserts an item into the Cuckoo Filter.
     *
     * @param item element to insert
     * @return true if successfully inserted; false if max kickouts reached (filter full)
     */
    public boolean put(T item) {
        if (item == null) {
            return false;
        }
        long h = hasher.hash(item);
        short fp = fingerprint(h);
        int i1 = index1(h);
        int i2 = altIndex(i1, fp);

        if (insertIntoBucket(i1, fp)) {
            count++;
            return true;
        }
        if (insertIntoBucket(i2, fp)) {
            count++;
            return true;
        }

        // Both candidate buckets are full -> perform Cuckoo kickout
        int curIndex = random.nextBoolean() ? i1 : i2;
        short curFp = fp;

        for (int kick = 0; kick < maxKicks; kick++) {
            int slot = random.nextInt(ENTRIES_PER_BUCKET);
            int offset = curIndex * ENTRIES_PER_BUCKET + slot;
            short evicted = table[offset];
            table[offset] = curFp;
            curFp = evicted;
            curIndex = altIndex(curIndex, curFp);

            if (insertIntoBucket(curIndex, curFp)) {
                count++;
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the item is probably in the Cuckoo Filter.
     *
     * @param item element to check
     * @return true if candidate fingerprint is present in bucket 1 or bucket 2
     */
    public boolean contains(T item) {
        if (item == null) {
            return false;
        }
        long h = hasher.hash(item);
        short fp = fingerprint(h);
        int i1 = index1(h);
        if (bucketContains(i1, fp)) {
            return true;
        }
        int i2 = altIndex(i1, fp);
        return bucketContains(i2, fp);
    }

    /**
     * Removes an item from the Cuckoo Filter.
     *
     * @param item element to remove
     * @return true if removed; false if item was not found
     */
    public boolean remove(T item) {
        if (item == null) {
            return false;
        }
        long h = hasher.hash(item);
        short fp = fingerprint(h);
        int i1 = index1(h);
        if (removeFromBucket(i1, fp)) {
            count--;
            return true;
        }
        int i2 = altIndex(i1, fp);
        if (removeFromBucket(i2, fp)) {
            count--;
            return true;
        }
        return false;
    }

    public int getCount() {
        return count;
    }

    public int getCapacity() {
        return numBuckets * ENTRIES_PER_BUCKET;
    }

    public int getNumBuckets() {
        return numBuckets;
    }

    public int getEntriesPerBucket() {
        return ENTRIES_PER_BUCKET;
    }

    public double getLoadFactor() {
        return (double) count / getCapacity();
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public void clear() {
        Arrays.fill(table, (short) 0);
        count = 0;
    }

    private boolean insertIntoBucket(int bucketIndex, short fp) {
        int offset = bucketIndex * ENTRIES_PER_BUCKET;
        for (int i = 0; i < ENTRIES_PER_BUCKET; i++) {
            if (table[offset + i] == 0) {
                table[offset + i] = fp;
                return true;
            }
        }
        return false;
    }

    private boolean bucketContains(int bucketIndex, short fp) {
        int offset = bucketIndex * ENTRIES_PER_BUCKET;
        for (int i = 0; i < ENTRIES_PER_BUCKET; i++) {
            if (table[offset + i] == fp) {
                return true;
            }
        }
        return false;
    }

    private boolean removeFromBucket(int bucketIndex, short fp) {
        int offset = bucketIndex * ENTRIES_PER_BUCKET;
        for (int i = 0; i < ENTRIES_PER_BUCKET; i++) {
            if (table[offset + i] == fp) {
                table[offset + i] = 0;
                return true;
            }
        }
        return false;
    }

    private short fingerprint(long hash) {
        short fp = (short) (hash & 0xFFFF);
        return fp != 0 ? fp : (short) 1;
    }

    private int index1(long hash) {
        return (int) ((hash >>> 16) & bucketMask);
    }

    private int altIndex(int index, short fp) {
        int delta = (int) (Hasher.mix64(fp & 0xFFFFL) & bucketMask);
        if (delta == 0) {
            delta = 1;
        }
        return (index ^ delta) & bucketMask;
    }
}
