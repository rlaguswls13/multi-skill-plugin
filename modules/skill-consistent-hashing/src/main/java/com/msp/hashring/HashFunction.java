package com.msp.hashring;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 64-bit hash function interface with built-in high-quality MurmurHash3 and FNV-1a implementations.
 */
@FunctionalInterface
public interface HashFunction {

    /**
     * Computes a 64-bit hash code for the given string key.
     *
     * @param key the input key
     * @return 64-bit hash code
     */
    long hash(String key);

    /**
     * Default 64-bit MurmurHash3 algorithm providing excellent avalanche properties and uniform distribution.
     */
    HashFunction MURMUR3_64 = key -> {
        Objects.requireNonNull(key, "key cannot be null");
        byte[] data = key.getBytes(StandardCharsets.UTF_8);
        return murmur3_64(data, 0x9747b28c);
    };

    /**
     * 64-bit FNV-1a (Fowler-Noll-Vo) hash algorithm for fast, lightweight hashing.
     */
    HashFunction FNV1A_64 = key -> {
        Objects.requireNonNull(key, "key cannot be null");
        byte[] data = key.getBytes(StandardCharsets.UTF_8);
        long hash = 0xcbf29ce484222325L;
        long fnvPrime = 0x100000001b3L;
        for (byte b : data) {
            hash ^= (b & 0xFF);
            hash *= fnvPrime;
        }
        return hash;
    };

    /**
     * MurmurHash3 64-bit finalizer / implementation.
     */
    private static long murmur3_64(byte[] data, int seed) {
        long h = seed & 0xFFFFFFFFL;
        long c1 = 0x87c37b91114253d5L;
        long c2 = 0x4cf5ad432745937fL;
        int len = data.length;
        int nblocks = len / 8;

        for (int i = 0; i < nblocks; i++) {
            int idx = i * 8;
            long k = (((long) data[idx] & 0xFF))
                    | (((long) data[idx + 1] & 0xFF) << 8)
                    | (((long) data[idx + 2] & 0xFF) << 16)
                    | (((long) data[idx + 3] & 0xFF) << 24)
                    | (((long) data[idx + 4] & 0xFF) << 32)
                    | (((long) data[idx + 5] & 0xFF) << 40)
                    | (((long) data[idx + 6] & 0xFF) << 48)
                    | (((long) data[idx + 7] & 0xFF) << 56);

            k *= c1;
            k = Long.rotateLeft(k, 31);
            k *= c2;
            h ^= k;
            h = Long.rotateLeft(h, 27);
            h = h * 5 + 0x52dce729L;
        }

        long k1 = 0;
        int tailStart = nblocks * 8;
        switch (len & 7) {
            case 7:
                k1 ^= ((long) data[tailStart + 6] & 0xFF) << 48;
            case 6:
                k1 ^= ((long) data[tailStart + 5] & 0xFF) << 40;
            case 5:
                k1 ^= ((long) data[tailStart + 4] & 0xFF) << 32;
            case 4:
                k1 ^= ((long) data[tailStart + 3] & 0xFF) << 24;
            case 3:
                k1 ^= ((long) data[tailStart + 2] & 0xFF) << 16;
            case 2:
                k1 ^= ((long) data[tailStart + 1] & 0xFF) << 8;
            case 1:
                k1 ^= ((long) data[tailStart] & 0xFF);
                k1 *= c1;
                k1 = Long.rotateLeft(k1, 31);
                k1 *= c2;
                h ^= k1;
                break;
            default:
                break;
        }

        h ^= len;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }
}
