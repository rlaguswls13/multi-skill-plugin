package com.msp.roaring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoaringBitmapLiteTest {

    @Test
    void testBasicAddContainsRemove() {
        RoaringBitmapLite bm = new RoaringBitmapLite();
        assertTrue(bm.isEmpty());

        bm.add(10);
        bm.add(100_000);
        bm.add(1_000_000);
        bm.add(10); // duplicate

        assertEquals(3, bm.cardinality());
        assertTrue(bm.contains(10));
        assertTrue(bm.contains(100_000));
        assertTrue(bm.contains(1_000_000));
        assertFalse(bm.contains(11));

        bm.remove(100_000);
        assertEquals(2, bm.cardinality());
        assertFalse(bm.contains(100_000));
        assertArrayEquals(new int[]{10, 1_000_000}, bm.toArray());
    }

    @Test
    void testContainerSwitchingFromArrayToBitmapAndBack() {
        RoaringBitmapLite bm = new RoaringBitmapLite();
        // Add 4096 non-contiguous elements in chunk 0 -> stays ArrayContainer
        for (int i = 0; i < 4096; i++) {
            bm.add(i * 2);
        }
        assertEquals(4096, bm.cardinality());
        assertInstanceOf(ArrayContainer.class, bm.getContainerForValue(0));

        // 4097th element triggers conversion to BitmapContainer
        bm.add(4096 * 2);
        assertEquals(4097, bm.cardinality());
        assertInstanceOf(BitmapContainer.class, bm.getContainerForValue(0));

        // Removing one element converts back to ArrayContainer
        bm.remove(4096 * 2);
        assertEquals(4096, bm.cardinality());
        assertInstanceOf(ArrayContainer.class, bm.getContainerForValue(0));
    }

    @Test
    void testRunContainerOptimization() {
        RoaringBitmapLite bm = new RoaringBitmapLite();
        bm.addRange(1000, 10_000); // 9000 contiguous integers in chunk 0
        bm.addRange(70_000, 70_500); // 500 contiguous integers in chunk 1

        assertEquals(9500, bm.cardinality());
        assertInstanceOf(BitmapContainer.class, bm.getContainerForValue(1000));
        assertInstanceOf(ArrayContainer.class, bm.getContainerForValue(70_000));

        bm.optimize();
        assertInstanceOf(RunContainer.class, bm.getContainerForValue(1000));
        assertInstanceOf(RunContainer.class, bm.getContainerForValue(70_000));

        RunContainer rc0 = (RunContainer) bm.getContainerForValue(1000);
        assertEquals(1, rc0.numberOfRuns());
        assertTrue(bm.contains(1000));
        assertTrue(bm.contains(9999));
        assertFalse(bm.contains(10_000));
        assertEquals(9500, bm.toArray().length);
    }

    @Test
    void testSetAlgebraAndOrXorAndNot() {
        RoaringBitmapLite a = RoaringBitmapLite.bitmapOf(1, 2, 3, 100_000, 100_001);
        RoaringBitmapLite b = RoaringBitmapLite.bitmapOf(2, 3, 4, 100_001, 200_000);

        RoaringBitmapLite andResult = a.and(b);
        assertArrayEquals(new int[]{2, 3, 100_001}, andResult.toArray());

        RoaringBitmapLite orResult = a.or(b);
        assertArrayEquals(new int[]{1, 2, 3, 4, 100_000, 100_001, 200_000}, orResult.toArray());

        RoaringBitmapLite xorResult = a.xor(b);
        assertArrayEquals(new int[]{1, 4, 100_000, 200_000}, xorResult.toArray());

        RoaringBitmapLite andNotResult = a.andNot(b);
        assertArrayEquals(new int[]{1, 100_000}, andNotResult.toArray());
    }

    @Test
    void testCrossContainerSetAlgebra() {
        RoaringBitmapLite dense = new RoaringBitmapLite();
        dense.addRange(0, 6000); // BitmapContainer

        RoaringBitmapLite sparse = new RoaringBitmapLite();
        sparse.add(10);
        sparse.add(5999);
        sparse.add(7000); // ArrayContainer

        RoaringBitmapLite inter = dense.and(sparse);
        assertArrayEquals(new int[]{10, 5999}, inter.toArray());

        RoaringBitmapLite union = dense.or(sparse);
        assertEquals(6001, union.cardinality());
        assertTrue(union.contains(7000));

        dense.optimize(); // RunContainer
        assertArrayEquals(new int[]{10, 5999}, dense.and(sparse).toArray());
    }

    @Test
    void testNegativeAndUnsignedBoundaryValues() {
        RoaringBitmapLite bm = new RoaringBitmapLite();
        bm.add(0);
        bm.add(Integer.MAX_VALUE);
        bm.add(-1); // 0xFFFFFFFF

        assertEquals(3, bm.cardinality());
        assertTrue(bm.contains(0));
        assertTrue(bm.contains(Integer.MAX_VALUE));
        assertTrue(bm.contains(-1));
    }
}
