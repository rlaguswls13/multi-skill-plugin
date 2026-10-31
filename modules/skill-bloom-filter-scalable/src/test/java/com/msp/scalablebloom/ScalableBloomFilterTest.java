package com.msp.scalablebloom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScalableBloomFilter Tests")
class ScalableBloomFilterTest {

    @Test
    @DisplayName("Zero false negatives: all inserted items must return true")
    void testNoFalseNegatives() {
        ScalableBloomFilter<String> filter = new ScalableBloomFilter<>(100, 0.01);

        List<String> items = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            String item = "item-" + i + "-" + UUID.randomUUID();
            items.add(item);
            filter.add(item);
        }

        // Must find all inserted items
        for (String item : items) {
            assertTrue(filter.contains(item), "Filter must contain previously added item: " + item);
        }

        assertEquals(500, filter.approxElementCount());
        // Layer count should have expanded: 100 (layer 0) + 200 (layer 1) + 400 (layer 2) -> at least 3 layers
        assertTrue(filter.layerCount() >= 3, "Expected at least 3 layers for 500 items with initial capacity 100");
        assertTrue(filter.totalCapacity() >= 700);
    }

    @Test
    @DisplayName("Dynamic scaling: layers expand geometrically as capacity fills")
    void testDynamicScaling() {
        int initialCap = 50;
        ScalableBloomFilter<Integer> filter = new ScalableBloomFilter<>(initialCap, 0.05, 0.85, 2);

        assertEquals(1, filter.layerCount());
        assertEquals(50, filter.totalCapacity());

        // Fill layer 0 (50 items)
        for (int i = 0; i < 50; i++) {
            filter.add(i);
        }
        assertEquals(1, filter.layerCount());

        // 51st item triggers expansion to layer 1 (capacity 100)
        filter.add(50);
        assertEquals(2, filter.layerCount());
        assertEquals(150, filter.totalCapacity()); // 50 + 100

        // Fill layer 1 (items 51 to 150)
        for (int i = 51; i < 150; i++) {
            filter.add(i);
        }
        assertEquals(2, filter.layerCount());

        // 151st item triggers layer 2 (capacity 200)
        filter.add(150);
        assertEquals(3, filter.layerCount());
        assertEquals(350, filter.totalCapacity()); // 50 + 100 + 200
    }

    @Test
    @DisplayName("False positive rate remains bounded within expectation")
    void testBoundedFalsePositiveRate() {
        double targetFpr = 0.05; // 5%
        ScalableBloomFilter<String> filter = new ScalableBloomFilter<>(200, targetFpr);

        int insertCount = 1000;
        for (int i = 0; i < insertCount; i++) {
            filter.add("inserted-key-" + i);
        }

        int queryCount = 2000;
        int falsePositives = 0;
        for (int i = 0; i < queryCount; i++) {
            String nonExistentKey = "unrelated-absent-key-" + i + "-" + UUID.randomUUID();
            if (filter.contains(nonExistentKey)) {
                falsePositives++;
            }
        }

        double empiricalFpr = (double) falsePositives / queryCount;
        // Empirical FPR should not wildly exceed target FPR (allow small statistical margin, e.g. < 10%)
        assertTrue(empiricalFpr < 0.10, "Empirical FPR " + empiricalFpr + " should be comfortably below 10%");
    }

    @Test
    @DisplayName("Concurrent reads and writes are thread-safe")
    void testConcurrency() throws InterruptedException {
        ScalableBloomFilter<String> filter = new ScalableBloomFilter<>(50, 0.01);
        int threads = 8;
        int itemsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemsPerThread; i++) {
                        String key = "thread-" + threadId + "-key-" + i;
                        filter.add(key);
                        assertTrue(filter.contains(key));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(finished, "Concurrent execution timed out");

        assertEquals(threads * itemsPerThread, filter.approxElementCount());
    }

    @Test
    @DisplayName("Constructor argument validations and null checks")
    void testValidations() {
        assertThrows(IllegalArgumentException.class, () -> new ScalableBloomFilter<>(0, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new ScalableBloomFilter<>(100, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new ScalableBloomFilter<>(100, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new ScalableBloomFilter<>(100, 0.01, 1.5, 2));
        assertThrows(IllegalArgumentException.class, () -> new ScalableBloomFilter<>(100, 0.01, 0.8, 1));

        ScalableBloomFilter<String> filter = new ScalableBloomFilter<>(100, 0.01);
        assertThrows(NullPointerException.class, () -> filter.add(null));
        assertThrows(NullPointerException.class, () -> filter.contains(null));
    }
}
