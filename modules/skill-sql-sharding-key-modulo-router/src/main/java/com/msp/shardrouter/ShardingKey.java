package com.msp.shardrouter;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed sharding key supporting {@code Long}, {@code String}, and {@code UUID} values.
 */
public sealed interface ShardingKey permits ShardingKey.LongKey, ShardingKey.StringKey, ShardingKey.UuidKey {

    /**
     * Computes a deterministic 64-bit hash of this sharding key.
     */
    long hash64();

    static LongKey of(long value) {
        return new LongKey(value);
    }

    static StringKey of(String value) {
        return new StringKey(value);
    }

    static UuidKey of(UUID value) {
        return new UuidKey(value);
    }

    record LongKey(long value) implements ShardingKey {
        @Override
        public long hash64() {
            return mix64(value);
        }
    }

    record StringKey(String value) implements ShardingKey {
        public StringKey {
            Objects.requireNonNull(value, "String sharding key must not be null");
        }

        @Override
        public long hash64() {
            return fnv1a64(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    record UuidKey(UUID value) implements ShardingKey {
        public UuidKey {
            Objects.requireNonNull(value, "UUID sharding key must not be null");
        }

        @Override
        public long hash64() {
            long combined = mix64(value.getMostSignificantBits()) ^ mix64(value.getLeastSignificantBits() * 0x9E3779B97F4A7C15L);
            return mix64(combined);
        }
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long fnv1a64(byte[] bytes) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : bytes) {
            hash ^= (b & 0xFFL);
            hash *= 0x100000001b3L;
        }
        return mix64(hash);
    }
}
