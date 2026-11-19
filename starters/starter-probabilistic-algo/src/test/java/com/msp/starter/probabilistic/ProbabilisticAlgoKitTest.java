package com.msp.starter.probabilistic;

import com.msp.bloom.BloomFilter;
import com.msp.bloom.Funnels;
import com.msp.countmin.AtomicCountMinSketch;
import com.msp.countmin.CountMinSketch;
import com.msp.countmin.HeavyHitter;
import com.msp.hashring.ConsistentHashRing;
import com.msp.hllplus.HyperLogLogPlus;
import com.msp.roaring.RoaringBitmapLite;
import com.msp.tdigest.TDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProbabilisticAlgoKitTest {

    @Test
    @DisplayName("1. BloomFilter membership and 0% false negatives")
    void testBloomFilter() {
        BloomFilter<String> strFilter = ProbabilisticAlgoKit.stringBloomFilter(1_000, 0.01);
        strFilter.put("alice@example.com");
        strFilter.put("bob@example.com");

        assertTrue(strFilter.mightContain("alice@example.com"));
        assertTrue(strFilter.mightContain("bob@example.com"));
        assertFalse(strFilter.mightContain("charlie@example.com"));

        BloomFilter<Long> longFilter = ProbabilisticAlgoKit.longBloomFilter(500, 0.01);
        longFilter.put(42L);
        assertTrue(longFilter.mightContain(42L));

        BloomFilter<Integer> intFilter = ProbabilisticAlgoKit.bloomFilter(Funnels.integerFunnel(), 500, 0.01);
        intFilter.put(99);
        assertTrue(intFilter.mightContain(99));
    }

    @Test
    @DisplayName("2. HyperLogLog++ cardinality estimation and lossless merge")
    void testHyperLogLogPlus() {
        HyperLogLogPlus hllA = ProbabilisticAlgoKit.hyperLogLog(12);
        HyperLogLogPlus hllB = ProbabilisticAlgoKit.hyperLogLog(12);

        for (int i = 0; i < 1_000; i++) {
            hllA.add("user-" + i);
        }
        for (int i = 500; i < 1_500; i++) {
            hllB.add("user-" + i);
        }

        hllA.merge(hllB);
        long estimated = hllA.estimate();
        assertTrue(estimated >= 1_350 && estimated <= 1_650,
                "Expected merged cardinality near 1500, got: " + estimated);

        HyperLogLogPlus defaultHll = ProbabilisticAlgoKit.hyperLogLog();
        defaultHll.add(100L);
        assertEquals(1L, defaultHll.estimate());
    }

    @Test
    @DisplayName("3. T-Digest streaming quantile accuracy (p50, p95, p99)")
    void testTDigest() {
        TDigest digest = ProbabilisticAlgoKit.tDigest(100.0);
        for (int i = 1; i <= 10_000; i++) {
            digest.add(i);
        }

        double p50 = digest.quantile(0.50);
        double p95 = digest.quantile(0.95);
        double p99 = digest.quantile(0.99);

        assertEquals(5_000.0, p50, 150.0);
        assertEquals(9_500.0, p95, 100.0);
        assertEquals(9_900.0, p99, 50.0);

        TDigest defaultDigest = ProbabilisticAlgoKit.tDigest();
        defaultDigest.add(42.0);
        assertEquals(42.0, defaultDigest.quantile(0.5));
    }

    @Test
    @DisplayName("4. Count-Min Sketch frequency estimation, heavy hitters, and atomic variant")
    void testCountMinSketch() {
        CountMinSketch<String> cms = ProbabilisticAlgoKit.countMinSketch(0.01, 0.01);
        cms.update("hot-key", 500);
        cms.update("cold-key", 3);

        assertTrue(cms.estimateFrequency("hot-key") >= 500);
        assertTrue(cms.estimateFrequency("cold-key") >= 3);

        CountMinSketch<String> heavy = ProbabilisticAlgoKit.countMinSketchWithHeavyHitters(0.01, 0.01, 0.25);
        heavy.update("dominant", 80);
        heavy.update("minor-1", 10);
        heavy.update("minor-2", 10);

        List<HeavyHitter<String>> hitters = heavy.getHeavyHitters();
        assertFalse(hitters.isEmpty());
        assertEquals("dominant", hitters.get(0).item());

        AtomicCountMinSketch<String> atomicCms = ProbabilisticAlgoKit.atomicCountMinSketch(0.01, 0.01);
        atomicCms.update("event-a", 120);
        assertTrue(atomicCms.estimateFrequency("event-a") >= 120);
    }

    @Test
    @DisplayName("5. Roaring Bitmap compressed set operations (OR, AND, XOR, optimize)")
    void testRoaringBitmap() {
        RoaringBitmapLite a = ProbabilisticAlgoKit.roaringBitmapOf(1, 2, 3, 100_000);
        RoaringBitmapLite b = ProbabilisticAlgoKit.roaringBitmap();
        b.addRange(2, 6); // {2, 3, 4, 5}
        b.optimize();

        RoaringBitmapLite intersection = a.and(b);
        assertArrayEquals(new int[]{2, 3}, intersection.toArray());

        RoaringBitmapLite union = a.or(b);
        assertEquals(6, union.cardinality());
        assertTrue(union.contains(100_000));
        assertTrue(union.contains(5));
    }

    @Test
    @DisplayName("6. Consistent Hash Ring key routing and replica selection")
    void testConsistentHashRing() {
        ConsistentHashRing<String> ring = ProbabilisticAlgoKit.consistentHashRing(
                100, List.of("shard-1", "shard-2", "shard-3"));

        String primary = ring.getNodeOrThrow("order:98765");
        assertNotNull(primary);
        assertTrue(List.of("shard-1", "shard-2", "shard-3").contains(primary));

        List<String> replicas = ring.getNodes("order:98765", 2);
        assertEquals(2, replicas.size());
        assertEquals(primary, replicas.get(0));
        assertNotEquals(replicas.get(0), replicas.get(1));

        ConsistentHashRing<String> defaultRing = ProbabilisticAlgoKit.consistentHashRing();
        defaultRing.addNode("node-a");
        assertEquals("node-a", defaultRing.getNodeOrThrow("any-key"));
    }
}
