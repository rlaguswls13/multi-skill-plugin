package com.msp.roaring;

import java.util.Arrays;

/**
 * Run-Length Encoded (RLE) 16-bit container storing interleaved {@code [start, runLengthMinusOne]} pairs.
 */
public final class RunContainer implements Container {

    // Interleaved [start0, lengthMinus1_0, start1, lengthMinus1_1, ...]
    private short[] valuesLengths;
    private int nbrRuns;

    public RunContainer() {
        this.valuesLengths = new short[8];
        this.nbrRuns = 0;
    }

    RunContainer(short[] valuesLengths, int nbrRuns) {
        this.valuesLengths = valuesLengths;
        this.nbrRuns = nbrRuns;
    }

    public int numberOfRuns() {
        return nbrRuns;
    }

    static RunContainer fromArrayContainer(ArrayContainer ac) {
        int size = ac.rawSize();
        if (size == 0) {
            return new RunContainer();
        }
        short[] content = ac.rawContent();
        int runs = ac.countRuns();
        short[] vl = new short[runs * 2];
        int runIdx = 0;
        int start = Short.toUnsignedInt(content[0]);
        int prev = start;
        for (int i = 1; i < size; i++) {
            int curr = Short.toUnsignedInt(content[i]);
            if (curr != prev + 1) {
                vl[runIdx * 2] = (short) start;
                vl[runIdx * 2 + 1] = (short) (prev - start);
                runIdx++;
                start = curr;
            }
            prev = curr;
        }
        vl[runIdx * 2] = (short) start;
        vl[runIdx * 2 + 1] = (short) (prev - start);
        runIdx++;
        return new RunContainer(vl, runIdx);
    }

    static RunContainer fromBitmapContainer(BitmapContainer bc, int expectedRuns) {
        short[] vl = new short[Math.max(2, expectedRuns * 2)];
        int runIdx = 0;
        long[] bmp = bc.rawBitmap();
        int currentStart = -1;
        int prevVal = -1;
        for (int i = 0; i < BitmapContainer.BITMAP_WORDS; i++) {
            long w = bmp[i];
            int base = i << 6;
            while (w != 0L) {
                int tz = Long.numberOfTrailingZeros(w);
                int val = base + tz;
                if (currentStart == -1) {
                    currentStart = val;
                    prevVal = val;
                } else if (val == prevVal + 1) {
                    prevVal = val;
                } else {
                    if (runIdx * 2 + 1 >= vl.length) {
                        vl = Arrays.copyOf(vl, vl.length * 2);
                    }
                    vl[runIdx * 2] = (short) currentStart;
                    vl[runIdx * 2 + 1] = (short) (prevVal - currentStart);
                    runIdx++;
                    currentStart = val;
                    prevVal = val;
                }
                w &= (w - 1);
            }
        }
        if (currentStart != -1) {
            if (runIdx * 2 + 1 >= vl.length) {
                vl = Arrays.copyOf(vl, vl.length * 2);
            }
            vl[runIdx * 2] = (short) currentStart;
            vl[runIdx * 2 + 1] = (short) (prevVal - currentStart);
            runIdx++;
        }
        return new RunContainer(vl, runIdx);
    }

    @Override
    public Container add(int low16) {
        if (contains(low16)) {
            return this;
        }
        return toBitmapOrArray().add(low16).optimize();
    }

    @Override
    public Container addRange(int startLow16, int endLow16Exclusive) {
        if (startLow16 >= endLow16Exclusive) {
            return this;
        }
        if (nbrRuns == 0) {
            short[] vl = new short[]{
                    (short) startLow16,
                    (short) (endLow16Exclusive - startLow16 - 1)
            };
            return new RunContainer(vl, 1);
        }
        return toBitmapContainer().addRange(startLow16, endLow16Exclusive).optimize();
    }

    @Override
    public Container remove(int low16) {
        if (!contains(low16)) {
            return this;
        }
        return toBitmapOrArray().remove(low16).optimize();
    }

    @Override
    public boolean contains(int low16) {
        int target = low16 & 0xFFFF;
        int low = 0;
        int high = nbrRuns - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int start = Short.toUnsignedInt(valuesLengths[mid * 2]);
            int end = start + Short.toUnsignedInt(valuesLengths[mid * 2 + 1]);
            if (target < start) {
                high = mid - 1;
            } else if (target > end) {
                low = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    @Override
    public int cardinality() {
        int sum = 0;
        for (int i = 0; i < nbrRuns; i++) {
            sum += Short.toUnsignedInt(valuesLengths[i * 2 + 1]) + 1;
        }
        return sum;
    }

    @Override
    public boolean isEmpty() {
        return nbrRuns == 0;
    }

    private Container toBitmapOrArray() {
        int card = cardinality();
        return card <= MAX_ARRAY_CARDINALITY ? toArrayContainer() : toBitmapContainer();
    }

    @Override
    public Container and(Container other) {
        return toBitmapOrArray().and(other);
    }

    @Override
    public Container or(Container other) {
        return toBitmapOrArray().or(other);
    }

    @Override
    public Container xor(Container other) {
        return toBitmapOrArray().xor(other);
    }

    @Override
    public Container andNot(Container other) {
        return toBitmapOrArray().andNot(other);
    }

    @Override
    public Container optimize() {
        int card = cardinality();
        if (card <= MAX_ARRAY_CARDINALITY && nbrRuns * 2 >= card) {
            return toArrayContainer();
        }
        return this;
    }

    @Override
    public BitmapContainer toBitmapContainer() {
        BitmapContainer bc = new BitmapContainer();
        for (int i = 0; i < nbrRuns; i++) {
            int start = Short.toUnsignedInt(valuesLengths[i * 2]);
            int len = Short.toUnsignedInt(valuesLengths[i * 2 + 1]) + 1;
            bc.addRange(start, start + len);
        }
        return bc;
    }

    @Override
    public ArrayContainer toArrayContainer() {
        int card = cardinality();
        short[] arr = new short[card];
        int pos = 0;
        for (int i = 0; i < nbrRuns; i++) {
            int start = Short.toUnsignedInt(valuesLengths[i * 2]);
            int len = Short.toUnsignedInt(valuesLengths[i * 2 + 1]) + 1;
            for (int v = start; v < start + len; v++) {
                arr[pos++] = (short) v;
            }
        }
        return new ArrayContainer(arr, pos);
    }

    @Override
    public int fillArray(int highBitsShifted, int[] dest, int offset) {
        int pos = offset;
        for (int i = 0; i < nbrRuns; i++) {
            int start = Short.toUnsignedInt(valuesLengths[i * 2]);
            int len = Short.toUnsignedInt(valuesLengths[i * 2 + 1]) + 1;
            for (int v = start; v < start + len; v++) {
                dest[pos++] = highBitsShifted | v;
            }
        }
        return pos;
    }

    @Override
    public Container copy() {
        return new RunContainer(Arrays.copyOf(valuesLengths, nbrRuns * 2), nbrRuns);
    }
}
