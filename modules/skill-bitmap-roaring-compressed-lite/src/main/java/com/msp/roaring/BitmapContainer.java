package com.msp.roaring;

import java.util.Arrays;

/**
 * Dense 16-bit container backed by {@code long[1024]} (65,536 bits = 8 KB) when cardinality &gt; 4096.
 */
public final class BitmapContainer implements Container {

    public static final int BITMAP_WORDS = 1024;
    private final long[] bitmap;
    private int cardinality;

    public BitmapContainer() {
        this.bitmap = new long[BITMAP_WORDS];
        this.cardinality = 0;
    }

    BitmapContainer(long[] bitmap, int cardinality) {
        this.bitmap = bitmap;
        this.cardinality = cardinality;
    }

    long[] rawBitmap() {
        return bitmap;
    }

    void addInPlace(int low16) {
        int v = low16 & 0xFFFF;
        int wordIdx = v >>> 6;
        long mask = 1L << (v & 63);
        long prev = bitmap[wordIdx];
        if ((prev & mask) == 0L) {
            bitmap[wordIdx] = prev | mask;
            cardinality++;
        }
    }

    @Override
    public Container add(int low16) {
        addInPlace(low16);
        return this;
    }

    @Override
    public Container addRange(int startLow16, int endLow16Exclusive) {
        int s = Math.max(0, startLow16);
        int e = Math.min(MAX_CAPACITY, endLow16Exclusive);
        if (s >= e) {
            return this;
        }
        int firstWord = s >>> 6;
        int lastWord = (e - 1) >>> 6;
        if (firstWord == lastWord) {
            long mask = (~0L << (s & 63)) & (~0L >>> (64 - ((e - 1) & 63) - 1));
            cardinality -= Long.bitCount(bitmap[firstWord]);
            bitmap[firstWord] |= mask;
            cardinality += Long.bitCount(bitmap[firstWord]);
        } else {
            long firstMask = ~0L << (s & 63);
            cardinality -= Long.bitCount(bitmap[firstWord]);
            bitmap[firstWord] |= firstMask;
            cardinality += Long.bitCount(bitmap[firstWord]);

            for (int w = firstWord + 1; w < lastWord; w++) {
                cardinality -= Long.bitCount(bitmap[w]);
                bitmap[w] = ~0L;
                cardinality += 64;
            }

            long lastMask = ~0L >>> (63 - ((e - 1) & 63));
            cardinality -= Long.bitCount(bitmap[lastWord]);
            bitmap[lastWord] |= lastMask;
            cardinality += Long.bitCount(bitmap[lastWord]);
        }
        return this;
    }

    @Override
    public Container remove(int low16) {
        int v = low16 & 0xFFFF;
        int wordIdx = v >>> 6;
        long mask = 1L << (v & 63);
        long prev = bitmap[wordIdx];
        if ((prev & mask) != 0L) {
            bitmap[wordIdx] = prev & ~mask;
            cardinality--;
            if (cardinality <= MAX_ARRAY_CARDINALITY) {
                return toArrayContainer();
            }
        }
        return this;
    }

    @Override
    public boolean contains(int low16) {
        int v = low16 & 0xFFFF;
        return (bitmap[v >>> 6] & (1L << (v & 63))) != 0L;
    }

    @Override
    public int cardinality() {
        return cardinality;
    }

    @Override
    public boolean isEmpty() {
        return cardinality == 0;
    }

    @Override
    public Container and(Container other) {
        if (other instanceof ArrayContainer ac) {
            return ac.and(this);
        }
        BitmapContainer obc = other.toBitmapContainer();
        long[] res = new long[BITMAP_WORDS];
        int card = 0;
        for (int i = 0; i < BITMAP_WORDS; i++) {
            long w = this.bitmap[i] & obc.bitmap[i];
            res[i] = w;
            card += Long.bitCount(w);
        }
        BitmapContainer out = new BitmapContainer(res, card);
        return card <= MAX_ARRAY_CARDINALITY ? out.toArrayContainer() : out;
    }

    @Override
    public Container or(Container other) {
        BitmapContainer obc = other.toBitmapContainer();
        long[] res = new long[BITMAP_WORDS];
        int card = 0;
        for (int i = 0; i < BITMAP_WORDS; i++) {
            long w = this.bitmap[i] | obc.bitmap[i];
            res[i] = w;
            card += Long.bitCount(w);
        }
        BitmapContainer out = new BitmapContainer(res, card);
        return card <= MAX_ARRAY_CARDINALITY ? out.toArrayContainer() : out;
    }

    @Override
    public Container xor(Container other) {
        BitmapContainer obc = other.toBitmapContainer();
        long[] res = new long[BITMAP_WORDS];
        int card = 0;
        for (int i = 0; i < BITMAP_WORDS; i++) {
            long w = this.bitmap[i] ^ obc.bitmap[i];
            res[i] = w;
            card += Long.bitCount(w);
        }
        BitmapContainer out = new BitmapContainer(res, card);
        return card <= MAX_ARRAY_CARDINALITY ? out.toArrayContainer() : out;
    }

    @Override
    public Container andNot(Container other) {
        BitmapContainer obc = other.toBitmapContainer();
        long[] res = new long[BITMAP_WORDS];
        int card = 0;
        for (int i = 0; i < BITMAP_WORDS; i++) {
            long w = this.bitmap[i] & ~obc.bitmap[i];
            res[i] = w;
            card += Long.bitCount(w);
        }
        BitmapContainer out = new BitmapContainer(res, card);
        return card <= MAX_ARRAY_CARDINALITY ? out.toArrayContainer() : out;
    }

    @Override
    public Container optimize() {
        if (cardinality <= MAX_ARRAY_CARDINALITY) {
            return toArrayContainer().optimize();
        }
        int runs = countRuns();
        // BitmapContainer uses 8192 bytes (4096 shorts); RunContainer uses 2 shorts (4 bytes) per run.
        if (runs * 2 < 2048) {
            return RunContainer.fromBitmapContainer(this, runs);
        }
        return this;
    }

    int countRuns() {
        int runs = 0;
        long nextBit = 0L;
        for (int i = 0; i < BITMAP_WORDS; i++) {
            long word = bitmap[i];
            runs += Long.bitCount((~word) & (word << 1)) + (int) (word & (~nextBit) & 1L);
            nextBit = word >>> 63;
        }
        return runs;
    }

    @Override
    public BitmapContainer toBitmapContainer() {
        return new BitmapContainer(Arrays.copyOf(bitmap, BITMAP_WORDS), cardinality);
    }

    @Override
    public ArrayContainer toArrayContainer() {
        short[] arr = new short[cardinality];
        int pos = 0;
        for (int i = 0; i < BITMAP_WORDS; i++) {
            long w = bitmap[i];
            int base = i << 6;
            while (w != 0L) {
                int tz = Long.numberOfTrailingZeros(w);
                arr[pos++] = (short) (base + tz);
                w &= (w - 1);
            }
        }
        return new ArrayContainer(arr, pos);
    }

    @Override
    public int fillArray(int highBitsShifted, int[] dest, int offset) {
        int pos = offset;
        for (int i = 0; i < BITMAP_WORDS; i++) {
            long w = bitmap[i];
            int base = highBitsShifted | (i << 6);
            while (w != 0L) {
                int tz = Long.numberOfTrailingZeros(w);
                dest[pos++] = base + tz;
                w &= (w - 1);
            }
        }
        return pos;
    }

    @Override
    public Container copy() {
        return toBitmapContainer();
    }
}
