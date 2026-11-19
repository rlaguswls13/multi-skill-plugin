package com.msp.rabinkarp;

/**
 * Collision-resistant 128-bit-space double polynomial hash pair.
 */
public record DoubleHash(long h1, long h2) implements Comparable<DoubleHash> {

    @Override
    public int compareTo(DoubleHash other) {
        int cmp = Long.compare(this.h1, other.h1);
        return (cmp != 0) ? cmp : Long.compare(this.h2, other.h2);
    }
}
