package com.msp.bloom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class BloomFilterTest {

    @Nested
    @DisplayName("Mathematical Sizing Tests")
    class SizingTests {

        @Test
        @DisplayName("Optimal bit size and hash count calculations")
        void testOptimalFormulas() {
            // For n = 1,000, p = 0.01:
            // m = -1000 * ln(0.01) / (ln 2)^2 = 4605.17 / 0.480453 = 9585.05 -> 9586
            int m = BloomFilter.optimalBitSize(1000, 0.01);
            assertEquals(9586, m);

            // k = (m / n) * ln 2 = 9.586 * 0.693147 = 6.64 -> 7
            int k = BloomFilter.optimalHashCount(1000, m);
            assertEquals(7, k);
        }

        @Test
        @DisplayName("Invalid parameters throw IllegalArgumentException")
        void testInvalidParameters() {
            assertThrows(IllegalArgumentException.class, () -> BloomFilter.optimalBitSize(0, 0.01));
            assertThrows(IllegalArgumentException.class, () -> BloomFilter.optimalBitSize(100, 0.0));
            assertThrows(IllegalArgumentException.class, () -> BloomFilter.optimalBitSize(100, 1.0));
            assertThrows(IllegalArgumentException.class, () -> BloomFilter.optimalBitSize(100, -0.5));
            assertThrows(IllegalArgumentException.class, () -> BloomFilter.optimalHashCount(0, 100));
            assertThrows(IllegalArgumentException.class, () -> BloomFilter.optimalHashCount(100, 0));
        }
    }

    @Nested
    @DisplayName("Probabilistic Membership Tests")
    class MembershipTests {

        @Test
        @DisplayName("0% false negatives: all added elements must return true on mightContain")
        void testZeroFalseNegatives() {
            int insertions = 5_000;
            BloomFilter<String> filter = BloomFilter.create(Funnels.stringFunnel(), insertions, 0.01);

            for (int i = 0; i < insertions; i++) {
                filter.put("user:account:" + i);
            }

            assertEquals(insertions, filter.elementCount());
            assertTrue(filter.bitsSetCount() > 0);

            // Verify 100% of inserted elements are detected as present
            for (int i = 0; i < insertions; i++) {
                assertTrue(filter.mightContain("user:account:" + i),
                        "Inserted item 'user:account:" + i + "' was not found! Bloom filters must have 0% false negatives.");
            }
        }

        @Test
        @DisplayName("False positive rate on 10,000 absent queries stays within tolerance of target FPP")
        void testFalsePositiveRate() {
            int insertions = 2_000;
            double targetFpp = 0.03; // 3%
            BloomFilter<String> filter = BloomFilter.create(Funnels.stringFunnel(), insertions, targetFpp);

            for (int i = 0; i < insertions; i++) {
                filter.put("existing-" + i);
            }

            // Query 10,000 distinct items that were never inserted
            int queries = 10_000;
            int falsePositives = 0;
            for (int i = 0; i < queries; i++) {
                if (filter.mightContain("non-existent-probe-key-" + i)) {
                    falsePositives++;
                }
            }

            double actualFpp = (double) falsePositives / queries;
            // Target is 3% (0.03). With 10,000 queries, actual should be within [0.01, 0.045]
            assertTrue(actualFpp <= 0.045,
                    String.format("Actual FPP (%.4f) exceeded tolerance for target FPP (%.2f)", actualFpp, targetFpp));
        }

        @Test
        @DisplayName("Different funnels for Long and byte[]")
        void testDifferentFunnels() {
            BloomFilter<Long> longFilter = BloomFilter.create(Funnels.longFunnel(), 100, 0.01);
            longFilter.put(123456789L);
            longFilter.put(987654321L);

            assertTrue(longFilter.mightContain(123456789L));
            assertTrue(longFilter.mightContain(987654321L));
            assertFalse(longFilter.mightContain(555555555L));

            BloomFilter<byte[]> byteFilter = BloomFilter.create(Funnels.byteArrayFunnel(), 100, 0.01);
            byte[] payload = new byte[]{1, 2, 3, 4};
            byteFilter.put(payload);

            assertTrue(byteFilter.mightContain(new byte[]{1, 2, 3, 4}));
            assertFalse(byteFilter.mightContain(new byte[]{5, 6, 7, 8}));
        }

        @Test
        @DisplayName("Reset clears filter state")
        void testReset() {
            BloomFilter<String> filter = BloomFilter.create(Funnels.stringFunnel(), 100, 0.01);
            filter.put("hello");
            assertTrue(filter.mightContain("hello"));
            assertEquals(1, filter.elementCount());

            filter.reset();
            assertEquals(0, filter.elementCount());
            assertEquals(0, filter.bitsSetCount());
            assertFalse(filter.mightContain("hello"));
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Thread-safe concurrent puts and queries")
        void testConcurrentOperations() throws InterruptedException {
            int threads = 8;
            int itemsPerThread = 500;
            BloomFilter<String> filter = BloomFilter.create(Funnels.stringFunnel(), threads * itemsPerThread, 0.01);

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < itemsPerThread; i++) {
                            filter.put("item-" + threadId + "-" + i);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertEquals(threads * itemsPerThread, filter.elementCount());

            // All items must still be present
            for (int t = 0; t < threads; t++) {
                for (int i = 0; i < itemsPerThread; i++) {
                    assertTrue(filter.mightContain("item-" + t + "-" + i));
                }
            }
        }
    }
}
