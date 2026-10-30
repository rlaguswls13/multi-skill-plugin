package com.msp.diskcache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LruDiskCacheTest {

    @Test
    @DisplayName("Put and get String / byte array retrieves stored value")
    void testPutAndGet(@TempDir Path tempDir) throws IOException {
        try (LruDiskCache cache = new LruDiskCache(tempDir)) {
            cache.put("user:101", "Alice in Wonderland", Duration.ofMinutes(10));
            cache.put("raw:bytes", new byte[]{1, 2, 3, 4, 5}, null);

            Optional<String> textVal = cache.getString("user:101");
            assertTrue(textVal.isPresent());
            assertEquals("Alice in Wonderland", textVal.get());

            Optional<byte[]> byteVal = cache.get("raw:bytes");
            assertTrue(byteVal.isPresent());
            assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, byteVal.get());

            Optional<String> nonExistent = cache.getString("does-not-exist");
            assertTrue(nonExistent.isEmpty());
        }
    }

    @Test
    @DisplayName("TTL expiration deletes file and returns empty")
    void testTtlExpiration(@TempDir Path tempDir) throws IOException, InterruptedException {
        try (LruDiskCache cache = new LruDiskCache(tempDir)) {
            // Short TTL of 50 milliseconds
            cache.put("temp:token", "secret123", Duration.ofMillis(50));

            assertTrue(cache.getString("temp:token").isPresent());

            // Wait until expired
            Thread.sleep(100);

            // Accessing expired item should return empty and delete entry
            Optional<String> expiredVal = cache.getString("temp:token");
            assertTrue(expiredVal.isEmpty());
        }
    }

    @Test
    @DisplayName("CleanExpired purges all expired files in directory")
    void testCleanExpired(@TempDir Path tempDir) throws IOException, InterruptedException {
        try (LruDiskCache cache = new LruDiskCache(tempDir)) {
            cache.put("exp1", "data1", Duration.ofMillis(50));
            cache.put("exp2", "data2", Duration.ofMillis(50));
            cache.put("live", "data3", Duration.ofHours(1));

            Thread.sleep(100);

            int purged = cache.cleanExpired();
            assertEquals(2, purged);

            assertTrue(cache.getString("exp1").isEmpty());
            assertTrue(cache.getString("exp2").isEmpty());
            assertTrue(cache.getString("live").isPresent());
        }
    }

    @Test
    @DisplayName("Remove deletes entry successfully")
    void testRemove(@TempDir Path tempDir) throws IOException {
        try (LruDiskCache cache = new LruDiskCache(tempDir)) {
            cache.put("item", "data", null);
            assertTrue(cache.getString("item").isPresent());

            boolean removed = cache.remove("item");
            assertTrue(removed);
            assertTrue(cache.getString("item").isEmpty());

            boolean removedAgain = cache.remove("item");
            assertFalse(removedAgain);
        }
    }

    @Test
    @DisplayName("EvictIfExceeds removes least recently accessed items first")
    void testEvictIfExceeds(@TempDir Path tempDir) throws IOException, InterruptedException {
        try (LruDiskCache cache = new LruDiskCache(tempDir)) {
            cache.put("key1", "payload_one_11111111111111111111", null);
            Thread.sleep(20);
            cache.put("key2", "payload_two_22222222222222222222", null);
            Thread.sleep(20);
            cache.put("key3", "payload_three_3333333333333333333", null);
            Thread.sleep(20);

            // Access key1 to make it most recently used
            cache.getString("key1");

            // Key order of access now: key2 (oldest), key3 (middle), key1 (most recent)
            // Evict with small threshold (e.g. 100 bytes)
            int evicted = cache.evictIfExceeds(150);
            assertTrue(evicted >= 1);

            // key2 must have been evicted before key1
            assertTrue(cache.getString("key2").isEmpty());
            assertTrue(cache.getString("key1").isPresent());
        }
    }
}
