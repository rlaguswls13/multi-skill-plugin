package com.msp.cuckoo;

import java.nio.charset.StandardCharsets;

/**
 * Functional interface for computing 64-bit hash codes for Cuckoo Filter items.
 */
@FunctionalInterface
public interface Hasher<T> {

    /**
     * Computes a 64-bit hash for the given item.
     */
    long hash(T item);

    static Hasher<String> ofString() {
        return s -> s == null ? 0L : hashBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    static Hasher<byte[]> ofBytes() {
        return b -> b == null ? 0L : hashBytes(b);
    }

    static Hasher<Long> ofLong() {
        return l -> l == null ? 0L : mix64(l);
    }

    static <T> Hasher<T> ofDefault() {
        return item -> item == null ? 0L : mix64(item.hashCode());
    }

    static long hashBytes(byte[] data) {
        // FNV-1a 64-bit followed by SplitMix64 avalanche
        long h = 0xcbf29ce484222325L;
        for (byte b : data) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        return mix64(h);
    }

    static long mix64(long z) {
        // SplitMix64 finalizer for high entropy
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
