package com.msp.minhash;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable MinHash signature consisting of K 64-bit minimum hash values.
 */
public final class MinHashSignature {

    private final long[] hashes;

    public MinHashSignature(long[] hashes) {
        Objects.requireNonNull(hashes, "hashes array must not be null");
        if (hashes.length == 0) {
            throw new IllegalArgumentException("hashes array must not be empty");
        }
        this.hashes = hashes.clone();
    }

    public int size() {
        return hashes.length;
    }

    public long get(int index) {
        return hashes[index];
    }

    public long[] toArray() {
        return hashes.clone();
    }

    /**
     * Estimates Jaccard similarity between this signature and another signature.
     * Calculated as the fraction of matching hash values: (matches / K).
     */
    public double similarity(MinHashSignature other) {
        Objects.requireNonNull(other, "other signature must not be null");
        if (this.hashes.length != other.hashes.length) {
            throw new IllegalArgumentException("Signatures must have identical dimensions: "
                    + this.hashes.length + " != " + other.hashes.length);
        }
        int matches = 0;
        for (int i = 0; i < hashes.length; i++) {
            if (this.hashes[i] == other.hashes[i]) {
                matches++;
            }
        }
        return (double) matches / hashes.length;
    }

    /**
     * Serializes signature to byte array (8 bytes per hash).
     */
    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(hashes.length * 8);
        for (long h : hashes) {
            buf.putLong(h);
        }
        return buf.array();
    }

    public static MinHashSignature fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (bytes.length % 8 != 0 || bytes.length == 0) {
            throw new IllegalArgumentException("Byte array length must be a non-zero multiple of 8, got: " + bytes.length);
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        long[] hashes = new long[bytes.length / 8];
        for (int i = 0; i < hashes.length; i++) {
            hashes[i] = buf.getLong();
        }
        return new MinHashSignature(hashes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MinHashSignature that)) return false;
        return Arrays.equals(hashes, that.hashes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(hashes);
    }

    @Override
    public String toString() {
        return "MinHashSignature[size=" + hashes.length + "]";
    }
}
