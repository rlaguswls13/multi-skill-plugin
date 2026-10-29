package com.msp.bloom;

/**
 * High-performance 128-bit MurmurHash3 implementation for Kirsch-Mitzenmacher double-hashing.
 */
final class Murmur3 {

    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;

    private Murmur3() {
    }

    public static long[] hash128(byte[] data, int offset, int length, int seed) {
        long h1 = seed & 0xFFFFFFFFL;
        long h2 = seed & 0xFFFFFFFFL;

        int nblocks = length / 16;
        for (int i = 0; i < nblocks; i++) {
            int index = offset + i * 16;
            long k1 = getLongLittleEndian(data, index);
            long k2 = getLongLittleEndian(data, index + 8);

            k1 *= C1;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= C2;
            h1 ^= k1;

            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729;

            k2 *= C2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= C1;
            h2 ^= k2;

            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5;
        }

        int tailIndex = offset + nblocks * 16;
        int tailLength = length & 15;
        long k1 = 0;
        long k2 = 0;

        switch (tailLength) {
            case 15: k2 ^= ((long) data[tailIndex + 14] & 0xFF) << 48;
            case 14: k2 ^= ((long) data[tailIndex + 13] & 0xFF) << 40;
            case 13: k2 ^= ((long) data[tailIndex + 12] & 0xFF) << 32;
            case 12: k2 ^= ((long) data[tailIndex + 11] & 0xFF) << 24;
            case 11: k2 ^= ((long) data[tailIndex + 10] & 0xFF) << 16;
            case 10: k2 ^= ((long) data[tailIndex + 9] & 0xFF) << 8;
            case 9:  k2 ^= ((long) data[tailIndex + 8] & 0xFF);
                     k2 *= C2;
                     k2 = Long.rotateLeft(k2, 33);
                     k2 *= C1;
                     h2 ^= k2;
            case 8:  k1 ^= ((long) data[tailIndex + 7] & 0xFF) << 56;
            case 7:  k1 ^= ((long) data[tailIndex + 6] & 0xFF) << 48;
            case 6:  k1 ^= ((long) data[tailIndex + 5] & 0xFF) << 40;
            case 5:  k1 ^= ((long) data[tailIndex + 4] & 0xFF) << 32;
            case 4:  k1 ^= ((long) data[tailIndex + 3] & 0xFF) << 24;
            case 3:  k1 ^= ((long) data[tailIndex + 2] & 0xFF) << 16;
            case 2:  k1 ^= ((long) data[tailIndex + 1] & 0xFF) << 8;
            case 1:  k1 ^= ((long) data[tailIndex] & 0xFF);
                     k1 *= C1;
                     k1 = Long.rotateLeft(k1, 31);
                     k1 *= C2;
                     h1 ^= k1;
        }

        h1 ^= length;
        h2 ^= length;

        h1 += h2;
        h2 += h1;

        h1 = fmix64(h1);
        h2 = fmix64(h2);

        h1 += h2;
        h2 += h1;

        return new long[] { h1, h2 };
    }

    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    private static long getLongLittleEndian(byte[] data, int index) {
        return (((long) data[index] & 0xFF))
                | (((long) data[index + 1] & 0xFF) << 8)
                | (((long) data[index + 2] & 0xFF) << 16)
                | (((long) data[index + 3] & 0xFF) << 24)
                | (((long) data[index + 4] & 0xFF) << 32)
                | (((long) data[index + 5] & 0xFF) << 40)
                | (((long) data[index + 6] & 0xFF) << 48)
                | (((long) data[index + 7] & 0xFF) << 56);
    }
}
