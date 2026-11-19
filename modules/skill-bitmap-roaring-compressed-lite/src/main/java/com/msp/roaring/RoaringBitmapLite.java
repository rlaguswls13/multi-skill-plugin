package com.msp.roaring;

import java.util.Arrays;
import java.util.Objects;

/**
 * Compressed 32-bit Integer Set modeled on Roaring Bitmaps.
 * <p>
 * Partitions 32-bit integers into 16-bit chunks keyed by the most-significant 16 bits,
 * dynamically switching each chunk between {@link ArrayContainer}, {@link BitmapContainer},
 * and {@link RunContainer}.
 */
public final class RoaringBitmapLite {

    private short[] keys;
    private Container[] containers;
    private int size;

    public RoaringBitmapLite() {
        this.keys = new short[4];
        this.containers = new Container[4];
        this.size = 0;
    }

    private RoaringBitmapLite(short[] keys, Container[] containers, int size) {
        this.keys = keys;
        this.containers = containers;
        this.size = size;
    }

    public static RoaringBitmapLite bitmapOf(int... values) {
        RoaringBitmapLite rb = new RoaringBitmapLite();
        for (int v : values) {
            rb.add(v);
        }
        return rb;
    }

    private static int unsignedBinarySearch(short[] keys, int size, int unsignedKey) {
        int low = 0;
        int high = size - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = Short.toUnsignedInt(keys[mid]);
            if (midVal < unsignedKey) {
                low = mid + 1;
            } else if (midVal > unsignedKey) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    private void insertContainerAt(int index, short key, Container c) {
        if (size >= keys.length) {
            int newCap = Math.max(4, keys.length * 2);
            keys = Arrays.copyOf(keys, newCap);
            containers = Arrays.copyOf(containers, newCap);
        }
        if (index < size) {
            System.arraycopy(keys, index, keys, index + 1, size - index);
            System.arraycopy(containers, index, containers, index + 1, size - index);
        }
        keys[index] = key;
        containers[index] = c;
        size++;
    }

    private void removeContainerAt(int index) {
        int moved = size - index - 1;
        if (moved > 0) {
            System.arraycopy(keys, index + 1, keys, index, moved);
            System.arraycopy(containers, index + 1, containers, index, moved);
        }
        size--;
        containers[size] = null;
    }

    /**
     * Adds a 32-bit integer to this bitmap.
     */
    public void add(int x) {
        int hb = x >>> 16;
        int lb = x & 0xFFFF;
        int idx = unsignedBinarySearch(keys, size, hb);
        if (idx >= 0) {
            containers[idx] = containers[idx].add(lb);
        } else {
            ArrayContainer ac = new ArrayContainer();
            insertContainerAt(-idx - 1, (short) hb, ac.add(lb));
        }
    }

    /**
     * Adds a half-open range of non-negative integers {@code [start, end)} to this bitmap.
     */
    public void addRange(int start, int end) {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid unsigned range [" + start + ", " + end + ")");
        }
        if (start == end) {
            return;
        }
        int firstBucket = start >>> 16;
        int lastBucket = (end - 1) >>> 16;
        for (int bucket = firstBucket; bucket <= lastBucket; bucket++) {
            int bucketStart = (bucket == firstBucket) ? (start & 0xFFFF) : 0;
            int bucketEnd = (bucket == lastBucket) ? (((end - 1) & 0xFFFF) + 1) : Container.MAX_CAPACITY;
            int idx = unsignedBinarySearch(keys, size, bucket);
            if (idx >= 0) {
                containers[idx] = containers[idx].addRange(bucketStart, bucketEnd);
            } else {
                int len = bucketEnd - bucketStart;
                Container c = (len > Container.MAX_ARRAY_CARDINALITY)
                        ? new BitmapContainer().addRange(bucketStart, bucketEnd)
                        : new ArrayContainer().addRange(bucketStart, bucketEnd);
                insertContainerAt(-idx - 1, (short) bucket, c);
            }
        }
    }

    /**
     * Removes a 32-bit integer from this bitmap.
     */
    public void remove(int x) {
        int hb = x >>> 16;
        int lb = x & 0xFFFF;
        int idx = unsignedBinarySearch(keys, size, hb);
        if (idx >= 0) {
            Container updated = containers[idx].remove(lb);
            if (updated.isEmpty()) {
                removeContainerAt(idx);
            } else {
                containers[idx] = updated;
            }
        }
    }

    /**
     * Tests whether the 32-bit integer is present in this bitmap.
     */
    public boolean contains(int x) {
        int hb = x >>> 16;
        int idx = unsignedBinarySearch(keys, size, hb);
        return idx >= 0 && containers[idx].contains(x & 0xFFFF);
    }

    /**
     * Returns total number of integers stored in this bitmap.
     */
    public int cardinality() {
        int total = 0;
        for (int i = 0; i < size; i++) {
            total += containers[i].cardinality();
        }
        return total;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Converts eligible containers to {@link RunContainer} (RLE) where space savings occur.
     */
    public void optimize() {
        for (int i = 0; i < size; i++) {
            containers[i] = containers[i].optimize();
        }
    }

    /**
     * Returns the number of 16-bit containers currently allocated.
     */
    public int containerCount() {
        return size;
    }

    /**
     * Inspects the container type for a given high 16-bit chunk key.
     */
    public Container getContainerForValue(int x) {
        int idx = unsignedBinarySearch(keys, size, x >>> 16);
        return idx >= 0 ? containers[idx] : null;
    }

    /**
     * Computes the intersection (AND) of this bitmap and another bitmap.
     */
    public RoaringBitmapLite and(RoaringBitmapLite other) {
        Objects.requireNonNull(other, "Other bitmap must not be null");
        short[] resKeys = new short[Math.min(this.size, other.size)];
        Container[] resContainers = new Container[resKeys.length];
        int i = 0, j = 0, k = 0;
        while (i < this.size && j < other.size) {
            int k1 = Short.toUnsignedInt(this.keys[i]);
            int k2 = Short.toUnsignedInt(other.keys[j]);
            if (k1 == k2) {
                Container c = this.containers[i].and(other.containers[j]);
                if (!c.isEmpty()) {
                    resKeys[k] = (short) k1;
                    resContainers[k] = c;
                    k++;
                }
                i++;
                j++;
            } else if (k1 < k2) {
                i++;
            } else {
                j++;
            }
        }
        return new RoaringBitmapLite(resKeys, resContainers, k);
    }

    /**
     * Computes the union (OR) of this bitmap and another bitmap.
     */
    public RoaringBitmapLite or(RoaringBitmapLite other) {
        Objects.requireNonNull(other, "Other bitmap must not be null");
        short[] resKeys = new short[this.size + other.size];
        Container[] resContainers = new Container[resKeys.length];
        int i = 0, j = 0, k = 0;
        while (i < this.size && j < other.size) {
            int k1 = Short.toUnsignedInt(this.keys[i]);
            int k2 = Short.toUnsignedInt(other.keys[j]);
            if (k1 == k2) {
                resKeys[k] = (short) k1;
                resContainers[k] = this.containers[i].or(other.containers[j]);
                k++;
                i++;
                j++;
            } else if (k1 < k2) {
                resKeys[k] = this.keys[i];
                resContainers[k] = this.containers[i].copy();
                k++;
                i++;
            } else {
                resKeys[k] = other.keys[j];
                resContainers[k] = other.containers[j].copy();
                k++;
                j++;
            }
        }
        while (i < this.size) {
            resKeys[k] = this.keys[i];
            resContainers[k] = this.containers[i].copy();
            k++;
            i++;
        }
        while (j < other.size) {
            resKeys[k] = other.keys[j];
            resContainers[k] = other.containers[j].copy();
            k++;
            j++;
        }
        return new RoaringBitmapLite(resKeys, resContainers, k);
    }

    /**
     * Computes the symmetric difference (XOR) of this bitmap and another bitmap.
     */
    public RoaringBitmapLite xor(RoaringBitmapLite other) {
        Objects.requireNonNull(other, "Other bitmap must not be null");
        short[] resKeys = new short[this.size + other.size];
        Container[] resContainers = new Container[resKeys.length];
        int i = 0, j = 0, k = 0;
        while (i < this.size && j < other.size) {
            int k1 = Short.toUnsignedInt(this.keys[i]);
            int k2 = Short.toUnsignedInt(other.keys[j]);
            if (k1 == k2) {
                Container c = this.containers[i].xor(other.containers[j]);
                if (!c.isEmpty()) {
                    resKeys[k] = (short) k1;
                    resContainers[k] = c;
                    k++;
                }
                i++;
                j++;
            } else if (k1 < k2) {
                resKeys[k] = this.keys[i];
                resContainers[k] = this.containers[i].copy();
                k++;
                i++;
            } else {
                resKeys[k] = other.keys[j];
                resContainers[k] = other.containers[j].copy();
                k++;
                j++;
            }
        }
        while (i < this.size) {
            resKeys[k] = this.keys[i];
            resContainers[k] = this.containers[i].copy();
            k++;
            i++;
        }
        while (j < other.size) {
            resKeys[k] = other.keys[j];
            resContainers[k] = other.containers[j].copy();
            k++;
            j++;
        }
        return new RoaringBitmapLite(resKeys, resContainers, k);
    }

    /**
     * Computes the difference (AND NOT) of this bitmap minus another bitmap.
     */
    public RoaringBitmapLite andNot(RoaringBitmapLite other) {
        Objects.requireNonNull(other, "Other bitmap must not be null");
        short[] resKeys = new short[this.size];
        Container[] resContainers = new Container[this.size];
        int i = 0, j = 0, k = 0;
        while (i < this.size && j < other.size) {
            int k1 = Short.toUnsignedInt(this.keys[i]);
            int k2 = Short.toUnsignedInt(other.keys[j]);
            if (k1 == k2) {
                Container c = this.containers[i].andNot(other.containers[j]);
                if (!c.isEmpty()) {
                    resKeys[k] = (short) k1;
                    resContainers[k] = c;
                    k++;
                }
                i++;
                j++;
            } else if (k1 < k2) {
                resKeys[k] = this.keys[i];
                resContainers[k] = this.containers[i].copy();
                k++;
                i++;
            } else {
                j++;
            }
        }
        while (i < this.size) {
            resKeys[k] = this.keys[i];
            resContainers[k] = this.containers[i].copy();
            k++;
            i++;
        }
        return new RoaringBitmapLite(resKeys, resContainers, k);
    }

    /**
     * Materializes all stored integers into an unsigned-sorted {@code int[]} array.
     */
    public int[] toArray() {
        int[] out = new int[cardinality()];
        int pos = 0;
        for (int i = 0; i < size; i++) {
            int highBitsShifted = Short.toUnsignedInt(keys[i]) << 16;
            pos = containers[i].fillArray(highBitsShifted, out, pos);
        }
        return out;
    }
}
