package com.msp.murmur3;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Ultra-fast, zero-dependency implementation of Austin Appleby's MurmurHash3 algorithm
 * supporting both 32-bit (x86_32) and 128-bit (x64_128) non-cryptographic hashing.
 */
public final class Murmur3 {

    private static final int DEFAULT_SEED = 0;

    // 32-bit constants
    private static final int C1_32 = 0xcc9e2d51;
    private static final int C2_32 = 0x1b873593;

    // 128-bit constants
    private static final long C1_128 = 0x87c37b91114253d5L;
    private static final long C2_128 = 0x4cf5ad432745937fL;

    private Murmur3() {
        // Utility class
    }

    /**
     * Computes the 32-bit MurmurHash3 (x86_32) for the given byte array slice and seed.
     *
     * @param data byte array (not null)
     * @param offset start offset
     * @param length number of bytes
     * @param seed initial hash seed
     * @return 32-bit integer hash
     */
    public static int hash32(byte[] data, int offset, int length, int seed) {
        Objects.requireNonNull(data, "Data byte array cannot be null");
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IndexOutOfBoundsException("Invalid offset=" + offset + ", length=" + length + ", arrayLength=" + data.length);
        }

        int h1 = seed;
        int nblocks = length / 4;

        for (int i = 0; i < nblocks; i++) {
            int index = offset + (i * 4);
            int k1 = (data[index] & 0xFF)
                    | ((data[index + 1] & 0xFF) << 8)
                    | ((data[index + 2] & 0xFF) << 16)
                    | ((data[index + 3] & 0xFF) << 24);

            k1 *= C1_32;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= C2_32;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        int tailIndex = offset + (nblocks * 4);
        int k1 = 0;
        int tail = length & 3;

        switch (tail) {
            case 3:
                k1 ^= (data[tailIndex + 2] & 0xFF) << 16;
                // fall through
            case 2:
                k1 ^= (data[tailIndex + 1] & 0xFF) << 8;
                // fall through
            case 1:
                k1 ^= (data[tailIndex] & 0xFF);
                k1 *= C1_32;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= C2_32;
                h1 ^= k1;
                break;
            default:
                break;
        }

        // Finalization
        h1 ^= length;
        h1 ^= (h1 >>> 16);
        h1 *= 0x85ebca6b;
        h1 ^= (h1 >>> 13);
        h1 *= 0xc2b2ae35;
        h1 ^= (h1 >>> 16);

        return h1;
    }

    /**
     * Computes the 32-bit MurmurHash3 for the entire byte array using seed 0.
     */
    public static int hash32(byte[] data) {
        return hash32(data, 0, data != null ? data.length : 0, DEFAULT_SEED);
    }

    /**
     * Computes the 32-bit MurmurHash3 for the entire byte array with the specified seed.
     */
    public static int hash32(byte[] data, int seed) {
        return hash32(data, 0, data != null ? data.length : 0, seed);
    }

    /**
     * Computes the 32-bit MurmurHash3 for the given UTF-8 text using seed 0.
     */
    public static int hash32(String text) {
        return hash32(text, DEFAULT_SEED);
    }

    /**
     * Computes the 32-bit MurmurHash3 for the given UTF-8 text with the specified seed.
     */
    public static int hash32(String text, int seed) {
        Objects.requireNonNull(text, "Text cannot be null");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return hash32(bytes, 0, bytes.length, seed);
    }

    /**
     * Computes the 128-bit MurmurHash3 (x64_128) for the given byte array slice and seed.
     *
     * @param data byte array (not null)
     * @param offset start offset
     * @param length number of bytes
     * @param seed initial hash seed
     * @return {@link Murmur128Result} containing two 64-bit words (h1, h2)
     */
    public static Murmur128Result hash128(byte[] data, int offset, int length, int seed) {
        Objects.requireNonNull(data, "Data byte array cannot be null");
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IndexOutOfBoundsException("Invalid offset=" + offset + ", length=" + length + ", arrayLength=" + data.length);
        }

        long h1 = seed & 0xFFFFFFFFL;
        long h2 = seed & 0xFFFFFFFFL;

        int nblocks = length / 16;
        for (int i = 0; i < nblocks; i++) {
            int index = offset + (i * 16);
            long k1 = getLongLittleEndian(data, index);
            long k2 = getLongLittleEndian(data, index + 8);

            k1 *= C1_128;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= C2_128;
            h1 ^= k1;

            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729L;

            k2 *= C2_128;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= C1_128;
            h2 ^= k2;

            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5L;
        }

        int tailIndex = offset + (nblocks * 16);
        long k1 = 0;
        long k2 = 0;
        int tail = length & 15;

        switch (tail) {
            case 15: k2 ^= ((long) (data[tailIndex + 14] & 0xFF)) << 48;
            case 14: k2 ^= ((long) (data[tailIndex + 13] & 0xFF)) << 40;
            case 13: k2 ^= ((long) (data[tailIndex + 12] & 0xFF)) << 32;
            case 12: k2 ^= ((long) (data[tailIndex + 11] & 0xFF)) << 24;
            case 11: k2 ^= ((long) (data[tailIndex + 10] & 0xFF)) << 16;
            case 10: k2 ^= ((long) (data[tailIndex + 9] & 0xFF)) << 8;
            case 9:  k2 ^= ((long) (data[tailIndex + 8] & 0xFF));
                     k2 *= C2_128;
                     k2 = Long.rotateLeft(k2, 33);
                     k2 *= C1_128;
                     h2 ^= k2;
            case 8:  k1 ^= ((long) (data[tailIndex + 7] & 0xFF)) << 56;
            case 7:  k1 ^= ((long) (data[tailIndex + 6] & 0xFF)) << 48;
            case 6:  k1 ^= ((long) (data[tailIndex + 5] & 0xFF)) << 40;
            case 5:  k1 ^= ((long) (data[tailIndex + 4] & 0xFF)) << 32;
            case 4:  k1 ^= ((long) (data[tailIndex + 3] & 0xFF)) << 24;
            case 3:  k1 ^= ((long) (data[tailIndex + 2] & 0xFF)) << 16;
            case 2:  k1 ^= ((long) (data[tailIndex + 1] & 0xFF)) << 8;
            case 1:  k1 ^= ((long) (data[tailIndex] & 0xFF));
                     k1 *= C1_128;
                     k1 = Long.rotateLeft(k1, 31);
                     k1 *= C2_128;
                     h1 ^= k1;
            default:
                break;
        }

        // Finalization
        h1 ^= length;
        h2 ^= length;

        h1 += h2;
        h2 += h1;

        h1 = fmix64(h1);
        h2 = fmix64(h2);

        h1 += h2;
        h2 += h1;

        return new Murmur128Result(h1, h2);
    }

    /**
     * Computes the 128-bit MurmurHash3 for the entire byte array using seed 0.
     */
    public static Murmur128Result hash128(byte[] data) {
        return hash128(data, 0, data != null ? data.length : 0, DEFAULT_SEED);
    }

    /**
     * Computes the 128-bit MurmurHash3 for the entire byte array with the specified seed.
     */
    public static Murmur128Result hash128(byte[] data, int seed) {
        return hash128(data, 0, data != null ? data.length : 0, seed);
    }

    /**
     * Computes the 128-bit MurmurHash3 for the given UTF-8 text using seed 0.
     */
    public static Murmur128Result hash128(String text) {
        return hash128(text, DEFAULT_SEED);
    }

    /**
     * Computes the 128-bit MurmurHash3 for the given UTF-8 text with the specified seed.
     */
    public static Murmur128Result hash128(String text, int seed) {
        Objects.requireNonNull(text, "Text cannot be null");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return hash128(bytes, 0, bytes.length, seed);
    }

    private static long getLongLittleEndian(byte[] data, int index) {
        return ((long) (data[index] & 0xFF))
                | (((long) (data[index + 1] & 0xFF)) << 8)
                | (((long) (data[index + 2] & 0xFF)) << 16)
                | (((long) (data[index + 3] & 0xFF)) << 24)
                | (((long) (data[index + 4] & 0xFF)) << 32)
                | (((long) (data[index + 5] & 0xFF)) << 40)
                | (((long) (data[index + 6] & 0xFF)) << 48)
                | (((long) (data[index + 7] & 0xFF)) << 56);
    }

    private static long fmix64(long k) {
        k ^= (k >>> 33);
        k *= 0xff51afd7ed558ccdL;
        k ^= (k >>> 33);
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= (k >>> 33);
        return k;
    }
}
