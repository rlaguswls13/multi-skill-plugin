package com.msp.tdigest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TDigestTest {

    @Test
    void uniformDistributionAccurateAcrossExtremeTailQuantiles() {
        TDigest digest = TDigest.create(200.0);
        Random rng = new Random(42L);
        int n = 50_000;
        double[] samples = new double[n];
        for (int i = 0; i < n; i++) {
            double v = rng.nextDouble() * 1000.0;
            samples[i] = v;
            digest.add(v);
        }
        Arrays.sort(samples);

        assertEquals(n, digest.count(), 1e-9);
        assertEquals(samples[0], digest.min(), 1e-9);
        assertEquals(samples[n - 1], digest.max(), 1e-9);

        assertEquals(samples[(int) (n * 0.50)], digest.p50(), 5.0);
        assertEquals(samples[(int) (n * 0.90)], digest.p90(), 4.0);
        assertEquals(samples[(int) (n * 0.95)], digest.p95(), 3.0);
        assertEquals(samples[(int) (n * 0.99)], digest.p99(), 2.0);
        assertEquals(samples[(int) (n * 0.999)], digest.p999(), 1.5);

        // CDF checks
        assertEquals(0.0, digest.cdf(-1.0), 1e-9);
        assertEquals(1.0, digest.cdf(1001.0), 1e-9);
        assertEquals(0.5, digest.cdf(500.0), 0.015);
        assertEquals(0.99, digest.cdf(990.0), 0.005);

        // Compression bounds centroid count well below sample count
        assertTrue(digest.centroidCount() < 500, "Expected compact centroids, got " + digest.centroidCount());
    }

    @Test
    void mergeDistributedDigestsMatchesCombinedDistribution() {
        TDigest shardA = TDigest.create(150.0);
        TDigest shardB = TDigest.create(150.0);
        Random rng = new Random(123L);

        for (int i = 0; i < 20_000; i++) {
            // Exponential-like latency distribution
            double latency = -Math.log(1.0 - rng.nextDouble()) * 50.0;
            if ((i & 1) == 0) {
                shardA.add(latency);
            } else {
                shardB.add(latency);
            }
        }

        shardA.merge(shardB);
        assertEquals(20_000.0, shardA.count(), 1e-9);
        // True exponential(mean=50): p50 ~ 34.66, p95 ~ 149.79, p99 ~ 230.26
        assertEquals(34.66, shardA.p50(), 3.0);
        assertEquals(149.79, shardA.p95(), 8.0);
        assertEquals(230.26, shardA.p99(), 12.0);
    }

    @Test
    void weightedObservationsAndBinarySerializationRoundTrip() {
        TDigest original = TDigest.create(100.0);
        original.add(10.0, 50.0);
        original.add(20.0, 30.0);
        original.add(100.0, 20.0);

        byte[] encoded = original.toBytes();
        TDigest restored = TDigest.fromBytes(encoded);

        assertEquals(original.compression(), restored.compression(), 1e-9);
        assertEquals(original.count(), restored.count(), 1e-9);
        assertEquals(original.min(), restored.min(), 1e-9);
        assertEquals(original.max(), restored.max(), 1e-9);
        assertEquals(original.centroidCount(), restored.centroidCount());
        assertEquals(original.p50(), restored.p50(), 1e-9);
        assertEquals(original.p95(), restored.p95(), 1e-9);
        assertEquals(original.cdf(20.0), restored.cdf(20.0), 1e-9);
    }

    @Test
    void emptyAndSingleValueBoundaryConditions() {
        TDigest empty = TDigest.create();
        assertEquals(0.0, empty.count());
        assertTrue(Double.isNaN(empty.min()));
        assertTrue(Double.isNaN(empty.max()));
        assertTrue(Double.isNaN(empty.quantile(0.5)));
        assertTrue(Double.isNaN(empty.cdf(10.0)));

        TDigest restoredEmpty = TDigest.fromBytes(empty.toBytes());
        assertEquals(0.0, restoredEmpty.count());

        empty.add(42.0);
        assertEquals(42.0, empty.min());
        assertEquals(42.0, empty.max());
        assertEquals(42.0, empty.quantile(0.0));
        assertEquals(42.0, empty.quantile(0.5));
        assertEquals(42.0, empty.quantile(1.0));
        assertEquals(0.0, empty.cdf(41.9));
        assertEquals(1.0, empty.cdf(42.0));
    }

    @Test
    void invalidInputsThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> TDigest.create(5.0));
        TDigest digest = TDigest.create();
        assertThrows(IllegalArgumentException.class, () -> digest.add(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> digest.add(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> digest.add(1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> digest.add(1.0, -5.0));
        assertThrows(IllegalArgumentException.class, () -> digest.quantile(-0.01));
        assertThrows(IllegalArgumentException.class, () -> digest.quantile(1.01));
        assertThrows(IllegalArgumentException.class, () -> TDigest.fromBytes(new byte[]{1, 2, 3}));
    }
}
