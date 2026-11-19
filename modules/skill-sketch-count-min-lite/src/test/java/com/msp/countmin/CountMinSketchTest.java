package com.msp.countmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class CountMinSketchTest {

    @Test
    @DisplayName("CountMinConfig calculates width and depth from epsilon and delta")
    void testConfigCalculations() {
        CountMinConfig config = CountMinConfig.of(0.001, 0.01);
        // w = ceil(e / 0.001) = 2719
        // d = ceil(ln(100)) = 5
        assertEquals(2719, config.width());
        assertEquals(5, config.depth());

        assertThrows(IllegalArgumentException.class, () -> CountMinConfig.of(-0.1, 0.01));
        assertThrows(IllegalArgumentException.class, () -> CountMinConfig.of(0.01, 1.5));
    }

    @Test
    @DisplayName("Basic frequency estimation guarantees no underestimation")
    void testFrequencyEstimation() {
        CountMinSketch<String> sketch = CountMinSketch.create(0.001, 0.01);

        sketch.update("apple", 500);
        sketch.update("banana", 200);
        sketch.update("cherry", 50);

        assertEquals(750, sketch.getTotalCount());

        // Count-Min guarantee: estimate >= actual
        assertTrue(sketch.estimateFrequency("apple") >= 500);
        assertTrue(sketch.estimateFrequency("banana") >= 200);
        assertTrue(sketch.estimateFrequency("cherry") >= 50);
        assertEquals(0, sketch.estimateFrequency("non-existent"));

        // Error bound: estimate <= actual + epsilon * N
        // epsilon * N = 0.001 * 750 = 0.75 -> estimate should be very close to actual
        assertTrue(sketch.estimateFrequency("apple") <= 500 + 5);
        assertTrue(sketch.estimateFrequency("banana") <= 200 + 5);
    }

    @Test
    @DisplayName("Conservative update reduces overestimation")
    void testConservativeUpdate() {
        CountMinSketch<String> standardSketch = CountMinSketch.create(100, 3);
        CountMinSketch<String> conservativeSketch = CountMinSketch.create(100, 3);

        for (int i = 0; i < 1000; i++) {
            String item = "item_" + (i % 20);
            standardSketch.update(item, 1);
            conservativeSketch.updateConservative(item, 1);
        }

        // Verify both do not underestimate
        for (int i = 0; i < 20; i++) {
            String item = "item_" + i;
            assertTrue(standardSketch.estimateFrequency(item) >= 50);
            assertTrue(conservativeSketch.estimateFrequency(item) >= 50);
            // Conservative estimate is <= standard estimate
            assertTrue(conservativeSketch.estimateFrequency(item) <= standardSketch.estimateFrequency(item));
        }
    }

    @Test
    @DisplayName("Heavy hitters detection and Top-K ranking")
    void testHeavyHitters() {
        // Stream of 10,000 items
        // "alpha": 2000 (20%)
        // "beta": 1000 (10%)
        // "gamma": 500 (5%)
        // others: noisy background
        CountMinSketch<String> sketch = CountMinSketch.createWithHeavyHitters(0.001, 0.01, 0.04);

        sketch.update("alpha", 2000);
        sketch.update("beta", 1000);
        sketch.update("gamma", 500);

        for (int i = 0; i < 6500; i++) {
            sketch.update("noise_" + i, 1);
        }

        assertEquals(10000, sketch.getTotalCount());

        List<HeavyHitter<String>> heavyHitters = sketch.getHeavyHitters();
        assertFalse(heavyHitters.isEmpty());

        // Alpha and beta must be detected as heavy hitters (>= 4% of 10000 = 400)
        assertTrue(heavyHitters.stream().anyMatch(h -> h.item().equals("alpha")));
        assertTrue(heavyHitters.stream().anyMatch(h -> h.item().equals("beta")));
        assertTrue(heavyHitters.stream().anyMatch(h -> h.item().equals("gamma")));

        List<HeavyHitter<String>> top2 = sketch.getTopK(2);
        assertEquals(2, top2.size());
        assertEquals("alpha", top2.get(0).item());
        assertEquals("beta", top2.get(1).item());
    }

    @Test
    @DisplayName("Sketch merge sums counters correctly")
    void testMerge() {
        CountMinSketch<String> sketchA = CountMinSketch.create(500, 5);
        CountMinSketch<String> sketchB = CountMinSketch.create(500, 5);

        sketchA.update("x", 100);
        sketchA.update("y", 50);

        sketchB.update("x", 200);
        sketchB.update("z", 80);

        CountMinSketch<String> merged = sketchA.merge(sketchB);
        assertEquals(430, merged.getTotalCount());
        assertTrue(merged.estimateFrequency("x") >= 300);
        assertTrue(merged.estimateFrequency("y") >= 50);
        assertTrue(merged.estimateFrequency("z") >= 80);
    }

    @Test
    @DisplayName("AtomicCountMinSketch concurrent updates with Virtual Threads")
    void testConcurrentUpdates() throws InterruptedException {
        AtomicCountMinSketch<String> concurrentSketch = AtomicCountMinSketch.createWithHeavyHitters(0.001, 0.01, 0.05);

        int threads = 20;
        int updatesPerThread = 500;
        CountDownLatch latch = new CountDownLatch(threads);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < updatesPerThread; i++) {
                            concurrentSketch.update("hot_key", 1);
                            concurrentSketch.update("other_" + (i % 10), 1);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        long expectedTotal = (long) threads * updatesPerThread * 2;
        assertEquals(expectedTotal, concurrentSketch.getTotalCount());
        assertTrue(concurrentSketch.estimateFrequency("hot_key") >= (long) threads * updatesPerThread);
    }
}
