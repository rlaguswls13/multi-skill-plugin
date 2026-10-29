package com.msp.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConcurrentLruCache Tests")
class ConcurrentLruCacheTest {

    @Nested
    @DisplayName("LRU Eviction Tests")
    class LruEvictionTests {

        @Test
        @DisplayName("Should evict the least recently used entry when capacity is exceeded")
        void lruEvictionOrder() {
            MemoryCache<String, Integer> cache = Caches.lru(3);

            cache.put("k1", 1);
            cache.put("k2", 2);
            cache.put("k3", 3);

            // All 3 present
            assertEquals(3, cache.size());

            // Adding 4th entry should evict "k1" (oldest)
            cache.put("k4", 4);

            assertEquals(3, cache.size());
            assertFalse(cache.get("k1").isPresent(), "k1 should have been evicted");
            assertTrue(cache.get("k2").isPresent());
            assertTrue(cache.get("k3").isPresent());
            assertTrue(cache.get("k4").isPresent());
        }

        @Test
        @DisplayName("Reading an entry should refresh its recency and prevent eviction")
        void accessRefreshesRecency() {
            MemoryCache<String, Integer> cache = Caches.lru(3);

            cache.put("k1", 1);
            cache.put("k2", 2);
            cache.put("k3", 3);

            // Access "k1", making "k2" the least recently used
            assertTrue(cache.get("k1").isPresent());

            // Insert "k4" -> "k2" should be evicted instead of "k1"
            cache.put("k4", 4);

            assertTrue(cache.get("k1").isPresent(), "k1 was recently accessed, should not be evicted");
            assertFalse(cache.get("k2").isPresent(), "k2 should be evicted as LRU");
            assertTrue(cache.get("k3").isPresent());
            assertTrue(cache.get("k4").isPresent());
        }

        @Test
        @DisplayName("Updating existing entry should not increase size or evict other entries")
        void updateExisting() {
            MemoryCache<String, String> cache = Caches.lru(2);

            cache.put("a", "alpha");
            cache.put("b", "bravo");
            cache.put("a", "alpha-updated");

            assertEquals(2, cache.size());
            assertEquals("alpha-updated", cache.get("a").orElseThrow());
            assertEquals("bravo", cache.get("b").orElseThrow());
        }
    }

    @Nested
    @DisplayName("TTL Expiration Tests")
    class TtlExpirationTests {

        @Test
        @DisplayName("Entry should expire after TTL duration")
        void perEntryTtlExpiration() throws InterruptedException {
            MemoryCache<String, String> cache = Caches.lru(10);

            cache.put("temp", "value", Duration.ofMillis(100));
            cache.put("perm", "value");

            assertTrue(cache.get("temp").isPresent());
            assertTrue(cache.get("perm").isPresent());

            // Sleep past TTL
            Thread.sleep(150);

            assertFalse(cache.get("temp").isPresent(), "temp should have expired");
            assertTrue(cache.get("perm").isPresent(), "perm should remain valid");
        }

        @Test
        @DisplayName("Default cache TTL should apply to put without explicit TTL")
        void defaultTtlExpiration() throws InterruptedException {
            MemoryCache<String, String> cache = Caches.lru(10, Duration.ofMillis(100));

            cache.put("k1", "v1");
            assertTrue(cache.get("k1").isPresent());

            Thread.sleep(150);

            assertFalse(cache.get("k1").isPresent(), "k1 should expire according to default TTL");
        }

        @Test
        @DisplayName("cleanUpExpired should sweep and report count of expired entries")
        void cleanUpExpired() throws InterruptedException {
            MemoryCache<String, Integer> cache = Caches.lru(10);

            cache.put("e1", 1, Duration.ofMillis(80));
            cache.put("e2", 2, Duration.ofMillis(80));
            cache.put("live", 3, Duration.ofSeconds(10));

            assertEquals(3, cache.size());

            Thread.sleep(120);

            int cleaned = cache.cleanUpExpired();
            assertEquals(2, cleaned);
            assertEquals(1, cache.size());
            assertTrue(cache.get("live").isPresent());
        }
    }

    @Nested
    @DisplayName("CacheStats & Performance Metrics Tests")
    class StatsTests {

        @Test
        @DisplayName("Should track hits, misses, evictions, and calculate rates accurately")
        void statsCalculation() {
            MemoryCache<String, String> cache = Caches.lru(2);

            // Miss
            cache.get("unknown");
            // Put and hit
            cache.put("a", "apple");
            cache.get("a");
            cache.get("a");
            // Eviction
            cache.put("b", "banana");
            cache.put("c", "cherry"); // evicts "a" (since "a" was accessed, but then "b" was put... wait, "a" was accessed, then "b" put, so "a" is older than "b", "a" is evicted)

            CacheStats stats = cache.stats();
            assertEquals(2, stats.hitCount());
            assertEquals(1, stats.missCount());
            assertEquals(3, stats.requestCount());
            assertEquals(1, stats.evictionCount());
            assertEquals(2.0 / 3.0, stats.hitRate(), 0.001);
            assertEquals(1.0 / 3.0, stats.missRate(), 0.001);
        }
    }

    @Nested
    @DisplayName("ComputeIfAbsent & General Operations Tests")
    class GeneralOperationsTests {

        @Test
        @DisplayName("computeIfAbsent computes once and caches result")
        void computeIfAbsent() {
            MemoryCache<String, Integer> cache = Caches.lru(10);
            AtomicInteger calls = new AtomicInteger(0);

            Integer val1 = cache.computeIfAbsent("foo", k -> {
                calls.incrementAndGet();
                return 42;
            });

            Integer val2 = cache.computeIfAbsent("foo", k -> {
                calls.incrementAndGet();
                return 999;
            });

            assertEquals(42, val1);
            assertEquals(42, val2);
            assertEquals(1, calls.get(), "Function should only execute once");
        }

        @Test
        @DisplayName("Invalidate and clear should empty cache")
        void invalidateAndClear() {
            MemoryCache<String, Integer> cache = Caches.lru(5);

            cache.put("x", 10);
            cache.put("y", 20);

            assertTrue(cache.invalidate("x"));
            assertFalse(cache.invalidate("x"));
            assertEquals(1, cache.size());

            cache.clear();
            assertEquals(0, cache.size());
            assertTrue(cache.isEmpty());
        }

        @Test
        @DisplayName("Concurrent reads and writes under multi-threaded stress")
        void concurrentReadWrite() throws InterruptedException {
            MemoryCache<Integer, String> cache = Caches.lru(100);

            int threads = 16;
            int opsPerThread = 200;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(1);

            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        latch.await();
                        for (int i = 0; i < opsPerThread; i++) {
                            int key = (threadId * 10) + (i % 20);
                            cache.put(key, "val-" + key);
                            cache.get(key);
                            if (i % 5 == 0) {
                                cache.computeIfAbsent(key + 1000, k -> "computed-" + k);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertTrue(cache.size() <= 100, "Cache size must respect maxCapacity");
            assertTrue(cache.stats().requestCount() > 0);
        }
    }
}
