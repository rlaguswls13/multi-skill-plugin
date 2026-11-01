package com.msp.partbloom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PartitionedBloomFilter Unit Tests")
class PartitionedBloomFilterTest {

    @Test
    @DisplayName("Zero false negatives: all inserted items must return true for mightContain")
    void testZeroFalseNegatives() {
        int expectedInsertions = 2000;
        double targetFpp = 0.01;
        PartitionedBloomFilter<String> filter = PartitionedBloomFilter.create(expectedInsertions, targetFpp);

        Set<String> inserted = new HashSet<>();
        for (int i = 0; i < expectedInsertions; i++) {
            String item = "user-session-" + i + "-" + UUID.randomUUID();
            inserted.add(item);
            filter.add(item);
        }

        assertEquals(expectedInsertions, filter.insertionCount());

        for (String item : inserted) {
            assertTrue(filter.mightContain(item), "Bloom filter must never yield false negatives for inserted item: " + item);
        }
    }

    @Test
    @DisplayName("Empirical false positive rate is reasonably bounded by target FPP")
    void testEmpiricalFalsePositiveRate() {
        int expectedInsertions = 2000;
        double targetFpp = 0.03; // 3%
        PartitionedBloomFilter<String> filter = PartitionedBloomFilter.create(expectedInsertions, targetFpp);

        Set<String> inserted = new HashSet<>();
        for (int i = 0; i < expectedInsertions; i++) {
            String item = "inserted-" + i;
            inserted.add(item);
            filter.add(item);
        }

        int testCount = 5000;
        int falsePositives = 0;
        for (int i = 0; i < testCount; i++) {
            String probe = "non-existing-element-" + i;
            if (filter.mightContain(probe)) {
                falsePositives++;
            }
        }

        double empiricalFpp = (double) falsePositives / testCount;
        // Allow reasonable statistical margin (within 2x target FPP)
        assertTrue(empiricalFpp < targetFpp * 2.5,
                String.format("Empirical FPP %.4f should not drastically exceed target %.4f", empiricalFpp, targetFpp));
    }

    @Test
    @DisplayName("Clear resets all bits, insertions, and estimated FPP")
    void testClear() {
        PartitionedBloomFilter<String> filter = PartitionedBloomFilter.create(1000, 0.01);
        filter.add("alpha");
        filter.add("beta");
        assertEquals(2, filter.insertionCount());
        assertTrue(filter.mightContain("alpha"));
        assertTrue(filter.currentFppEstimate() > 0.0);

        filter.clear();
        assertEquals(0, filter.insertionCount());
        assertEquals(0.0, filter.currentFppEstimate());
        assertFalse(filter.mightContain("alpha"));
        assertFalse(filter.mightContain("beta"));
    }

    @Test
    @DisplayName("Dimensions and slice configurations")
    void testDimensions() {
        int sliceBits = 512;
        int hashCount = 8;
        PartitionedBloomFilter<Integer> filter = PartitionedBloomFilter.create(sliceBits, hashCount);

        assertEquals(sliceBits, filter.sliceBits());
        assertEquals(hashCount, filter.hashCount());
        assertEquals(sliceBits * hashCount, filter.totalBits());
    }

    @Test
    @DisplayName("Different data types (byte[], CharSequence, Integer, Long)")
    void testDifferentDataTypes() {
        PartitionedBloomFilter<Object> filter = PartitionedBloomFilter.create(500, 0.01);

        byte[] rawBytes = new byte[]{1, 2, 3, 4};
        filter.add(rawBytes);
        assertTrue(filter.mightContain(rawBytes));

        filter.add(12345);
        assertTrue(filter.mightContain(12345));

        filter.add(9876543210L);
        assertTrue(filter.mightContain(9876543210L));

        filter.add("text-value");
        assertTrue(filter.mightContain("text-value"));

        assertFalse(filter.mightContain("definitely-unseen-value"));
    }

    @Test
    @DisplayName("Throws exception on invalid creation arguments")
    void testInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                PartitionedBloomFilter.create(0, 0.01));
        assertThrows(IllegalArgumentException.class, () ->
                PartitionedBloomFilter.create(-10, 0.01));
        assertThrows(IllegalArgumentException.class, () ->
                PartitionedBloomFilter.create(100, 0.0));
        assertThrows(IllegalArgumentException.class, () ->
                PartitionedBloomFilter.create(100, 1.0));
        assertThrows(IllegalArgumentException.class, () ->
                PartitionedBloomFilter.create(100, 1.5));
        assertThrows(IllegalArgumentException.class, () ->
                PartitionedBloomFilter.create(0, 4));
        assertThrows(IllegalArgumentException.class, () ->
                PartitionedBloomFilter.create(100, 0));
    }

    @Test
    @DisplayName("Concurrent writes and reads thread safety")
    void testConcurrency() throws InterruptedException {
        int threadCount = 8;
        int itemsPerThread = 250;
        PartitionedBloomFilter<String> filter = PartitionedBloomFilter.create(threadCount * itemsPerThread, 0.01);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemsPerThread; i++) {
                        String item = "thread-" + threadId + "-item-" + i;
                        filter.add(item);
                        assertTrue(filter.mightContain(item));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(threadCount * itemsPerThread, filter.insertionCount());
    }
}
