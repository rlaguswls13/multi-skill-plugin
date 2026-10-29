package com.msp.hll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HyperLogLogTest {

    @Test
    @DisplayName("Empty estimator has 0 cardinality")
    void testEmptyCardinality() {
        HyperLogLog hll = new HyperLogLog();
        assertEquals(0, hll.cardinality());
    }

    @Test
    @DisplayName("Small cardinality estimation via LinearCounting is near-exact")
    void testSmallCardinality() {
        HyperLogLog hll = new HyperLogLog(12);

        // 1 element
        hll.add("user-1");
        assertEquals(1, hll.cardinality());

        // 10 elements
        for (int i = 2; i <= 10; i++) {
            hll.add("user-" + i);
        }
        assertEquals(10, hll.cardinality());

        // 50 elements
        for (int i = 11; i <= 50; i++) {
            hll.add("user-" + i);
        }
        long estimated = hll.cardinality();
        assertTrue(Math.abs(estimated - 50) <= 1, "Estimated was " + estimated + ", expected ~50");
    }

    @Test
    @DisplayName("Idempotency: re-adding existing elements does not change cardinality")
    void testIdempotency() {
        HyperLogLog hll = new HyperLogLog(12);

        for (int i = 0; i < 50; i++) {
            hll.add("item-fixed-key");
        }
        assertEquals(1, hll.cardinality());

        for (int i = 0; i < 100; i++) {
            hll.add("key-" + (i % 10));
        }
        // Exactly 10 unique elements + "item-fixed-key" = 11 unique elements
        assertEquals(11, hll.cardinality());
    }

    @Test
    @DisplayName("Large cardinality estimation error is within theoretical bound 1.04/sqrt(m)")
    void testLargeCardinalityErrorBound() {
        int precision = 12; // m = 4096, expected standard error = 1.04 / 64 = ~1.625%
        HyperLogLog hll = new HyperLogLog(precision);
        double expectedError = hll.getExpectedError();
        assertEquals(1.04 / 64.0, expectedError, 0.0001);

        int distinctElements = 50_000;
        for (int i = 0; i < distinctElements; i++) {
            hll.add("visitor-" + i);
        }

        long estimate = hll.cardinality();
        double relativeError = Math.abs((double) estimate - distinctElements) / distinctElements;

        // With 3 standard deviations (3 * 1.625% = 4.875%), error is well within 5%
        assertTrue(relativeError < 0.05,
                "Relative error " + relativeError + " exceeded 5% limit for estimate " + estimate);
    }

    @Test
    @DisplayName("Merge two disjoint sets produces union cardinality")
    void testMergeDisjointSets() {
        HyperLogLog setA = new HyperLogLog(12);
        HyperLogLog setB = new HyperLogLog(12);

        int sizeA = 10_000;
        int sizeB = 10_000;

        for (int i = 0; i < sizeA; i++) {
            setA.add("A-" + i);
        }
        for (int i = 0; i < sizeB; i++) {
            setB.add("B-" + i);
        }

        HyperLogLog merged = HyperLogLog.merge(setA, setB);
        long estimate = merged.cardinality();
        int expectedTotal = sizeA + sizeB; // 20,000

        double relativeError = Math.abs((double) estimate - expectedTotal) / expectedTotal;
        assertTrue(relativeError < 0.05,
                "Relative error " + relativeError + " on merged disjoint sets exceeded bound");
    }

    @Test
    @DisplayName("Merge overlapping sets produces accurate unique count")
    void testMergeOverlappingSets() {
        HyperLogLog setA = new HyperLogLog(12);
        HyperLogLog setB = new HyperLogLog(12);

        // A has 0..14,999 (15,000)
        for (int i = 0; i < 15_000; i++) {
            setA.add("item-" + i);
        }
        // B has 5,000..19,999 (15,000)
        for (int i = 5_000; i < 20_000; i++) {
            setB.add("item-" + i);
        }

        setA.merge(setB);
        long estimate = setA.cardinality();
        int expectedTotal = 20_000;

        double relativeError = Math.abs((double) estimate - expectedTotal) / expectedTotal;
        assertTrue(relativeError < 0.05,
                "Relative error " + relativeError + " on overlapping sets exceeded bound");
    }

    @Test
    @DisplayName("Support for long primitives and byte arrays")
    void testPrimitiveAndByteInputs() {
        HyperLogLog hll = new HyperLogLog(10);

        for (long i = 0; i < 1_000; i++) {
            hll.add(i);
        }
        long longEstimate = hll.cardinality();
        assertTrue(Math.abs(longEstimate - 1_000) < 50);

        byte[] rawBytes = new byte[]{1, 2, 3, 4, 5};
        hll.add(rawBytes);
        assertTrue(hll.cardinality() >= 1_000);
    }

    @Test
    @DisplayName("Reset clears all registers")
    void testReset() {
        HyperLogLog hll = new HyperLogLog(10);
        for (int i = 0; i < 500; i++) {
            hll.add("uuid-" + UUID.randomUUID());
        }
        assertTrue(hll.cardinality() > 400);

        hll.reset();
        assertEquals(0, hll.cardinality());
    }

    @Test
    @DisplayName("Defensive copying of registers")
    void testDefensiveCopy() {
        HyperLogLog hll = new HyperLogLog(6);
        hll.add("test");
        byte[] copy1 = hll.getRegisters();
        byte[] copy2 = hll.getRegisters();

        assertNotSame(copy1, copy2);
        copy1[0] = 100;
        assertEquals(hll.getRegisters()[0], copy2[0]);
    }

    @Test
    @DisplayName("Precision validation and merge precision mismatch")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () -> new HyperLogLog(3));
        assertThrows(IllegalArgumentException.class, () -> new HyperLogLog(17));

        HyperLogLog hll10 = new HyperLogLog(10);
        HyperLogLog hll12 = new HyperLogLog(12);
        assertThrows(IllegalArgumentException.class, () -> hll10.merge(hll12));
    }
}
