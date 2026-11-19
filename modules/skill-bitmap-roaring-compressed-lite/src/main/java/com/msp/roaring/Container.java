package com.msp.roaring;

/**
 * 16-bit Roaring Bitmap Container storing unsigned 16-bit integers (0..65535).
 */
public sealed interface Container permits ArrayContainer, BitmapContainer, RunContainer {

    int MAX_ARRAY_CARDINALITY = 4096;
    int MAX_CAPACITY = 1 << 16;

    Container add(int low16);

    Container addRange(int startLow16, int endLow16Exclusive);

    Container remove(int low16);

    boolean contains(int low16);

    int cardinality();

    boolean isEmpty();

    Container and(Container other);

    Container or(Container other);

    Container xor(Container other);

    Container andNot(Container other);

    Container optimize();

    BitmapContainer toBitmapContainer();

    ArrayContainer toArrayContainer();

    int fillArray(int highBitsShifted, int[] dest, int offset);

    Container copy();
}
