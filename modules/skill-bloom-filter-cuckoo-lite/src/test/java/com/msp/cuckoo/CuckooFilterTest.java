package com.msp.cuckoo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CuckooFilter Unit Tests")
class CuckooFilterTest {

    @Test
    @DisplayName("Basic put, contains, and count operations")
    void testBasicPutAndContains() {
        CuckooFilter<String> filter = CuckooFilter.createForStrings(64);
        assertTrue(filter.isEmpty());
        assertEquals(0, filter.getCount());

        assertTrue(filter.put("apple"));
        assertTrue(filter.put("banana"));
        assertTrue(filter.put("cherry"));

        assertEquals(3, filter.getCount());
        assertFalse(filter.isEmpty());

        assertTrue(filter.contains("apple"));
        assertTrue(filter.contains("banana"));
        assertTrue(filter.contains("cherry"));
        assertFalse(filter.contains("dragonfruit"));
    }

    @Test
    @DisplayName("Supports item deletion with remove()")
    void testRemove() {
        CuckooFilter<String> filter = CuckooFilter.createForStrings(64);
        filter.put("target-item");
        assertTrue(filter.contains("target-item"));
        assertEquals(1, filter.getCount());

        assertTrue(filter.remove("target-item"));
        assertFalse(filter.contains("target-item"));
        assertEquals(0, filter.getCount());

        // Removing non-existent item returns false
        assertFalse(filter.remove("not-found"));
    }

    @Test
    @DisplayName("High load factor insertion triggers Cuckoo kickouts successfully")
    void testHighLoadFactorAndKickouts() {
        int capacity = 512;
        CuckooFilter<String> filter = CuckooFilter.createForStrings(capacity);

        List<String> inserted = new ArrayList<>();
        int successCount = 0;
        for (int i = 0; i < 400; i++) {
            String item = "user-" + i + "-" + UUID.randomUUID();
            if (filter.put(item)) {
                inserted.add(item);
                successCount++;
            }
        }

        assertTrue(successCount > 350, "Cuckoo filter should achieve high load factor (>80%)");
        assertTrue(filter.getLoadFactor() > 0.6);

        // Verify inserted items are found
        for (String item : inserted) {
            assertTrue(filter.contains(item), "Inserted item must be found: " + item);
        }
    }

    @Test
    @DisplayName("Clear resets all entries and count")
    void testClear() {
        CuckooFilter<String> filter = CuckooFilter.createForStrings(64);
        filter.put("one");
        filter.put("two");
        assertEquals(2, filter.getCount());

        filter.clear();
        assertEquals(0, filter.getCount());
        assertTrue(filter.isEmpty());
        assertFalse(filter.contains("one"));
        assertFalse(filter.contains("two"));
    }

    @Test
    @DisplayName("False positive rate is extremely low for 16-bit fingerprints")
    void testFalsePositiveRate() {
        CuckooFilter<String> filter = CuckooFilter.createForStrings(1024);
        for (int i = 0; i < 500; i++) {
            filter.put("key-" + i);
        }

        int falsePositives = 0;
        int testQueries = 2000;
        for (int i = 0; i < testQueries; i++) {
            if (filter.contains("absent-key-" + i)) {
                falsePositives++;
            }
        }

        double fpr = (double) falsePositives / testQueries;
        assertTrue(fpr < 0.01, "False positive rate should be below 1%, was: " + fpr);
    }

    @Test
    @DisplayName("ConcurrentCuckooFilter handles concurrent insertions and queries safely")
    void testConcurrentCuckooFilter() throws InterruptedException {
        ConcurrentCuckooFilter<String> concurrent = ConcurrentCuckooFilter.createForStrings(1024);
        int threadCount = 8;
        int itemsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCounter = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemsPerThread; i++) {
                        String key = "thread-" + threadId + "-item-" + i;
                        if (concurrent.put(key)) {
                            successCounter.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(successCounter.get(), concurrent.getCount());
        assertTrue(concurrent.getCount() > 0);
    }

    record CustomItem(int id, String name) {}

    @Test
    @DisplayName("Supports custom objects with custom Hasher")
    void testCustomHasher() {
        Hasher<CustomItem> hasher = item -> Hasher.mix64((long) item.id() * 31 + item.name().hashCode());
        CuckooFilter<CustomItem> filter = CuckooFilter.create(hasher, 64);

        CustomItem a = new CustomItem(1, "Alice");
        CustomItem b = new CustomItem(2, "Bob");

        filter.put(a);
        assertTrue(filter.contains(a));
        assertFalse(filter.contains(b));
    }
}
