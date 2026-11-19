package com.msp.hllplus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HyperLogLogPlusTest {

    @Test
    void emptySketchEstimatesZeroAndStartsSparse() {
        HyperLogLogPlus hll = new HyperLogLogPlus();
        assertTrue(hll.isSparse());
        assertEquals(0L, hll.estimate());
        assertEquals(14, hll.precision());
        assertEquals(16384, hll.registerCount());
    }

    @Test
    void sparseModeHighAccuracyForSmallCardinality() {
        HyperLogLogPlus hll = new HyperLogLogPlus(14, 25);
        for (int i = 0; i < 500; i++) {
            hll.add("user-" + i);
            hll.add("user-" + i); // duplicates
        }
        assertTrue(hll.isSparse());
        long est = hll.estimate();
        double relErr = Math.abs(est - 500.0) / 500.0;
        assertTrue(relErr < 0.02, "Expected <2% error in sparse mode, got est=" + est);
    }

    @Test
    void sparseToDenseTransitionAndLargeCardinalityAccuracy() {
        HyperLogLogPlus hll = new HyperLogLogPlus(14, 25);
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            hll.add((long) i);
        }
        assertFalse(hll.isSparse());
        long est = hll.estimate();
        double relErr = Math.abs(est - (double) n) / n;
        assertTrue(relErr < 0.03, "Expected <3% error at 100k, got est=" + est + " (err=" + relErr + ")");
    }

    @Test
    void losslessMergeAcrossSparseAndDenseMatchesSingleSketch() {
        HyperLogLogPlus single = new HyperLogLogPlus(12, 25);
        HyperLogLogPlus partA = new HyperLogLogPlus(12, 25);
        HyperLogLogPlus partB = new HyperLogLogPlus(12, 25);
        HyperLogLogPlus partC = new HyperLogLogPlus(12, 25);

        // partA stays sparse (200 items), partB transitions to dense (5000 items), partC overlaps
        for (int i = 0; i < 200; i++) {
            partA.add("k-" + i);
            single.add("k-" + i);
        }
        for (int i = 100; i < 5100; i++) {
            partB.add("k-" + i);
            single.add("k-" + i);
        }
        for (int i = 4500; i < 4700; i++) {
            partC.add("k-" + i);
            single.add("k-" + i);
        }

        assertTrue(partA.isSparse());
        assertFalse(partB.isSparse());
        assertTrue(partC.isSparse());

        HyperLogLogPlus merged = HyperLogLogPlus.mergeAll(List.of(partA, partB, partC));
        assertEquals(single.estimate(), merged.estimate(), "Merged sketch must match single-pass sketch losslessly");
    }

    @Test
    void sparsePlusSparseMergeStaysSparseWhenBelowThreshold() {
        HyperLogLogPlus a = new HyperLogLogPlus(14, 25);
        HyperLogLogPlus b = new HyperLogLogPlus(14, 25);
        for (int i = 0; i < 100; i++) {
            a.add("a-" + i);
            b.add("b-" + i);
        }
        a.merge(b);
        assertTrue(a.isSparse());
        long est = a.estimate();
        assertTrue(Math.abs(est - 200) <= 5, "Expected ~200, got " + est);
    }

    @Test
    void binarySerializationRoundTripSparseAndDense() {
        // Sparse round-trip
        HyperLogLogPlus sparse = new HyperLogLogPlus(14, 25);
        for (int i = 0; i < 250; i++) {
            sparse.add(("item-" + i).getBytes());
        }
        byte[] sparseBytes = sparse.toBytes();
        assertTrue(sparseBytes.length < 16384, "Sparse serialization should be compact");
        HyperLogLogPlus restoredSparse = HyperLogLogPlus.fromBytes(sparseBytes);
        assertTrue(restoredSparse.isSparse());
        assertEquals(sparse.estimate(), restoredSparse.estimate());

        // Dense round-trip
        HyperLogLogPlus dense = new HyperLogLogPlus(10, 25);
        for (int i = 0; i < 5000; i++) {
            dense.add(i);
        }
        assertFalse(dense.isSparse());
        byte[] denseBytes = dense.toBytes();
        assertEquals(8 + 1024, denseBytes.length);
        HyperLogLogPlus restoredDense = HyperLogLogPlus.fromBytes(denseBytes);
        assertFalse(restoredDense.isSparse());
        assertEquals(dense.estimate(), restoredDense.estimate());
    }

    @Test
    void validationAndErrorConditions() {
        assertThrows(IllegalArgumentException.class, () -> new HyperLogLogPlus(3));
        assertThrows(IllegalArgumentException.class, () -> new HyperLogLogPlus(19));
        assertThrows(IllegalArgumentException.class, () -> new HyperLogLogPlus(14, 12));
        assertThrows(IllegalArgumentException.class, () -> new HyperLogLogPlus(14, 26));

        HyperLogLogPlus p10 = new HyperLogLogPlus(10);
        HyperLogLogPlus p12 = new HyperLogLogPlus(12);
        assertThrows(IllegalArgumentException.class, () -> p10.merge(p12));
        assertThrows(IllegalArgumentException.class, () -> HyperLogLogPlus.mergeAll(List.of()));

        assertThrows(IllegalArgumentException.class, () -> HyperLogLogPlus.fromBytes(new byte[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> HyperLogLogPlus.fromBytes(new byte[12]));
    }
}
