package com.msp.hllplus;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Pure Java 64-bit non-cryptographic hash functions (MurmurHash3 x64 and SplitMix64)
 * with strong avalanche properties suitable for HyperLogLog++ sketches.
 */
public final class Hash64 {

    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;
    private static final long DEFAULT_SEED = 0x9747b28cL;

    private Hash64() {
    }

    /**
     * Computes a 64-bit MurmurHash3 (x64 128-bit variant, returning h1) over {@code data}.
     */
    public static long murmur3(byte[] data) {
        return murmur3(data, DEFAULT_SEED);
    }

    /**
     * Computes a 64-bit MurmurHash3 (x64 128-bit variant, returning h1) over {@code data} with {@code seed}.
     */
    public static long murmur3(byte[] data, long seed) {
        Objects.requireNonNull(data, "data must not be null");
        int length = data.length;
        int nblocks = length >>> 4;

        long h1 = seed;
        long h2 = seed;

        for (int i = 0; i < nblocks; i++) {
            int offset = i << 4;
            long k1 = getLittleEndianLong(data, offset);
            long k2 = getLittleEndianLong(data, offset + 8);

            k1 *= C1;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= C2;
            h1 ^= k1;

            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729L;

            k2 *= C2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= C1;
            h2 ^= k2;

            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5L;
        }

        long k1 = 0L;
        long k2 = 0L;
        int tail = nblocks << 4;
        switch (length & 15) {
            case 15: k2 ^= (long) (data[tail + 14] & 0xFF) << 48;
            case 14: k2 ^= (long) (data[tail + 13] & 0xFF) << 40;
            case 13: k2 ^= (long) (data[tail + 12] & 0xFF) << 32;
            case 12: k2 ^= (long) (data[tail + 11] & 0xFF) << 24;
            case 11: k2 ^= (long) (data[tail + 10] & 0xFF) << 16;
            case 10: k2 ^= (long) (data[tail + 9] & 0xFF) << 8;
            case 9:
                k2 ^= (long) (data[tail + 8] & 0xFF);
                k2 *= C2;
                k2 = Long.rotateLeft(k2, 33);
                k2 *= C1;
                h2 ^= k2;
            case 8: k1 ^= (long) (data[tail + 7] & 0xFF) << 56;
            case 7: k1 ^= (long) (data[tail + 6] & 0xFF) << 48;
            case 6: k1 ^= (long) (data[tail + 5] & 0xFF) << 40;
            case 5: k1 ^= (long) (data[tail + 4] & 0xFF) << 32;
            case 4: k1 ^= (long) (data[tail + 3] & 0xFF) << 24;
            case 3: k1 ^= (long) (data[tail + 2] & 0xFF) << 16;
            case 2: k1 ^= (long) (data[tail + 1] & 0xFF) << 8;
            case 1:
                k1 ^= (long) (data[tail] & 0xFF);
                k1 *= C1;
                k1 = Long.rotateLeft(k1, 31);
                k1 *= C2;
                h1 ^= k1;
            default:
                break;
        }

        h1 ^= length;
        h2 ^= length;
        h1 += h2;
        h2 += h1;
        h1 = fmix64(h1);
        h2 = fmix64(h2);
        h1 += h2;
        return h1;
    }

    /**
     * Computes a 64-bit MurmurHash3 hash of a UTF-8 encoded string.
     */
    public static long murmur3(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return murmur3(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes a 64-bit SplitMix64 bijective hash of a {@code long} value.
     */
    public static long splitMix64(long value) {
        long z = value + 0x9e3779b97f4a7c15L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    private static long getLittleEndianLong(byte[] data, int offset) {
        return ((long) data[offset] & 0xFFL)
                | (((long) data[offset + 1] & 0xFFL) << 8)
                | (((long) data[offset + 2] & 0xFFL) << 16)
                | (((long) data[offset + 3] & 0xFFL) << 24)
                | (((long) data[offset + 4] & 0xFFL) << 32)
                | (((long) data[offset + 5] & 0xFFL) << 40)
                | (((long) data[offset + 6] & 0xFFL) << 48)
                | (((long) data[offset + 7] & 0xFFL) << 56);
    }
}
