package com.msp.cache;

import java.time.Duration;

/**
 * Unified factory facade for creating MemoryCache instances.
 */
public final class Caches {

    private Caches() {
    }

    /**
     * Creates an LRU cache with the specified capacity and no default TTL.
     */
    public static <K, V> MemoryCache<K, V> lru(int capacity) {
        return new ConcurrentLruCache<>(capacity);
    }

    /**
     * Creates an LRU cache with the specified capacity and default TTL.
     */
    public static <K, V> MemoryCache<K, V> lru(int capacity, Duration defaultTtl) {
        return new ConcurrentLruCache<>(capacity, defaultTtl);
    }

    /**
     * Creates a builder for fine-tuning cache configurations.
     */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    public static final class Builder<K, V> {
        private int maxCapacity = 1000;
        private Duration defaultTtl = null;

        private Builder() {
        }

        public Builder<K, V> maxCapacity(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("maxCapacity must be positive: " + capacity);
            }
            this.maxCapacity = capacity;
            return this;
        }

        public Builder<K, V> defaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
            return this;
        }

        public MemoryCache<K, V> build() {
            return new ConcurrentLruCache<>(maxCapacity, defaultTtl);
        }
    }
}
