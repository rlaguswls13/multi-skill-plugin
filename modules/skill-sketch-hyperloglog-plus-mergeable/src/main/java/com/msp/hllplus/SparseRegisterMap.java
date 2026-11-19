package com.msp.hllplus;

import java.util.Arrays;

/**
 * Primitive open-addressed map storing sparse HyperLogLog++ registers:
 * 25-bit sparse index {@code k} -> max {@code rho} (1..61) without boxing.
 */
final class SparseRegisterMap {

    private static final int EMPTY = -1;
    private static final float LOAD_FACTOR = 0.65f;

    private int[] keys;
    private byte[] values;
    private int mask;
    private int size;
    private int resizeThreshold;

    SparseRegisterMap(int initialCapacity) {
        int cap = tableSizeFor(Math.max(16, initialCapacity));
        this.keys = new int[cap];
        this.values = new byte[cap];
        Arrays.fill(this.keys, EMPTY);
        this.mask = cap - 1;
        this.resizeThreshold = (int) (cap * LOAD_FACTOR);
    }

    void putMax(int key, byte rho) {
        int slot = mix(key) & mask;
        while (true) {
            int existing = keys[slot];
            if (existing == EMPTY) {
                keys[slot] = key;
                values[slot] = rho;
                if (++size >= resizeThreshold) {
                    rehash();
                }
                return;
            }
            if (existing == key) {
                if (rho > values[slot]) {
                    values[slot] = rho;
                }
                return;
            }
            slot = (slot + 1) & mask;
        }
    }

    int size() {
        return size;
    }

    /**
     * Packs all entries as sorted 31-bit integers {@code (key << 6) | (rho & 0x3F)}.
     */
    int[] toSortedPackedArray() {
        int[] packed = new int[size];
        int pos = 0;
        for (int i = 0; i < keys.length; i++) {
            int k = keys[i];
            if (k != EMPTY) {
                packed[pos++] = (k << 6) | (values[i] & 0x3F);
            }
        }
        Arrays.sort(packed);
        return packed;
    }

    void forEach(EntryConsumer consumer) {
        for (int i = 0; i < keys.length; i++) {
            int k = keys[i];
            if (k != EMPTY) {
                consumer.accept(k, values[i]);
            }
        }
    }

    SparseRegisterMap copy() {
        SparseRegisterMap clone = new SparseRegisterMap(keys.length);
        System.arraycopy(this.keys, 0, clone.keys, 0, this.keys.length);
        System.arraycopy(this.values, 0, clone.values, 0, this.values.length);
        clone.size = this.size;
        return clone;
    }

    private void rehash() {
        int newCap = keys.length << 1;
        int[] oldKeys = keys;
        byte[] oldValues = values;
        keys = new int[newCap];
        values = new byte[newCap];
        Arrays.fill(keys, EMPTY);
        mask = newCap - 1;
        resizeThreshold = (int) (newCap * LOAD_FACTOR);
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY) {
                putMax(oldKeys[i], oldValues[i]);
            }
        }
    }

    private static int mix(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        return x;
    }

    private static int tableSizeFor(int cap) {
        int n = Integer.highestOneBit(cap - 1) << 1;
        return Math.max(16, n);
    }

    @FunctionalInterface
    interface EntryConsumer {
        void accept(int sparseIndex, byte rho);
    }
}
