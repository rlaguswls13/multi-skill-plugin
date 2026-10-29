package com.msp.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * General in-memory cache interface with TTL expiration and performance metrics.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface MemoryCache<K, V> {

    /**
     * Retrieves the value associated with the given key if present and not expired.
     */
    Optional<V> get(K key);

    /**
     * Retrieves the value associated with the given key, or returns defaultValue if absent or expired.
     */
    default V getOrDefault(K key, V defaultValue) {
        return get(key).orElse(defaultValue);
    }

    /**
     * Associates the specified value with the specified key using the cache's default TTL.
     */
    void put(K key, V value);

    /**
     * Associates the specified value with the specified key using a custom TTL.
     */
    void put(K key, V value, Duration ttl);

    /**
     * If the key is not already associated with a value, computes its value using the given mapping function.
     */
    default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        return computeIfAbsent(key, null, mappingFunction);
    }

    /**
     * If the key is not already associated with a value, computes its value using the given mapping function
     * and associates it with the given custom TTL.
     */
    V computeIfAbsent(K key, Duration ttl, Function<? super K, ? extends V> mappingFunction);

    /**
     * Invalidates the given key from the cache.
     *
     * @return true if the key was present and removed
     */
    boolean invalidate(K key);

    /**
     * Clears all entries from the cache.
     */
    void clear();

    /**
     * Returns the number of currently active entries in the cache.
     */
    int size();

    /**
     * Returns true if cache contains no entries.
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns a snapshot of cache statistics.
     */
    CacheStats stats();

    /**
     * Performs maintenance by removing expired entries.
     *
     * @return number of expired entries removed
     */
    int cleanUpExpired();
}
