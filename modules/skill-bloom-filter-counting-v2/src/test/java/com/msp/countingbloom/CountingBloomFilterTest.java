package com.msp.countingbloom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CountingBloomFilter Tests (4-bit and 8-bit)")
class CountingBloomFilterTest {

    @Test
    @DisplayName("Validation on create arguments")
    void testCreateArgumentValidation() {
        assertThrows(IllegalArgumentException.class, () -> CountingBloomFilter.create(0, 0.01, 4));
        assertThrows(IllegalArgumentException.class, () -> CountingBloomFilter.create(1000, 0.0, 4));
        assertThrows(IllegalArgumentException.class, () -> CountingBloomFilter.create(1000, 1.0, 4));
        assertThrows(IllegalArgumentException.class, () -> CountingBloomFilter.create(1000, 0.01, 5)); // Not 4 or 8
    }

    @Test
    @DisplayName("4-bit counters: Add, mightContain, and Remove lifecycle")
    void test4BitAddAndRemove() {
        CountingBloomFilter<String> filter = CountingBloomFilter.create(100, 0.01, 4);
        assertEquals(4, filter.getCounterBits());

        String item1 = "user_alpha_123";
        String item2 = "user_beta_456";

        assertFalse(filter.mightContain(item1));
        assertFalse(filter.mightContain(item2));
        assertEquals(0, filter.approximateElementCount());

        // Add item1
        assertTrue(filter.add(item1));
        assertTrue(filter.mightContain(item1));
        assertFalse(filter.mightContain(item2));
        assertEquals(1, filter.approximateElementCount());

        // Add item2
        assertTrue(filter.add(item2));
        assertTrue(filter.mightContain(item2));
        assertEquals(2, filter.approximateElementCount());

        // Remove item1
        assertTrue(filter.remove(item1));
        assertFalse(filter.mightContain(item1));
        assertTrue(filter.mightContain(item2));
        assertEquals(1, filter.approximateElementCount());

        // Remove item that doesn't exist
        assertFalse(filter.remove("non_existing_item"));
    }

    @Test
    @DisplayName("8-bit counters: Add and Remove lifecycle")
    void test8BitAddAndRemove() {
        CountingBloomFilter<String> filter = CountingBloomFilter.create(500, 0.01, 8);
        assertEquals(8, filter.getCounterBits());

        String item = "service_order_789";
        filter.add(item);
        assertTrue(filter.mightContain(item));
        assertEquals(1, filter.approximateElementCount());

        assertTrue(filter.remove(item));
        assertFalse(filter.mightContain(item));
        assertEquals(0, filter.approximateElementCount());
    }

    @Test
    @DisplayName("4-bit counter saturation at 15 prevents counter overflow")
    void test4BitSaturationBehavior() {
        CountingBloomFilter<String> filter = CountingBloomFilter.create(50, 0.05, 4);
        String frequentItem = "hot_cache_key";

        // Add 25 times (exceeds max 15)
        for (int i = 0; i < 25; i++) {
            filter.add(frequentItem);
        }
        assertEquals(25, filter.approximateElementCount());
        assertTrue(filter.mightContain(frequentItem));

        // When removing a saturated item, counters do not decrement to prevent false negatives
        assertTrue(filter.remove(frequentItem));
        assertTrue(filter.mightContain(frequentItem));
    }

    @Test
    @DisplayName("Clear resets all counters and count")
    void testClear() {
        CountingBloomFilter<String> filter = CountingBloomFilter.create(100, 0.01, 8);
        filter.add("itemA");
        filter.add("itemB");
        assertEquals(2, filter.approximateElementCount());

        filter.clear();
        assertEquals(0, filter.approximateElementCount());
        assertFalse(filter.mightContain("itemA"));
        assertFalse(filter.mightContain("itemB"));
    }

    @Test
    @DisplayName("Multithreaded concurrent additions and checks")
    void testConcurrentOperations() throws InterruptedException {
        CountingBloomFilter<String> filter = CountingBloomFilter.create(2000, 0.01, 8);
        int threadCount = 8;
        int itemsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemsPerThread; i++) {
                        String key = "thread_" + threadId + "_item_" + i;
                        filter.add(key);
                        assertTrue(filter.mightContain(key));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(threadCount * itemsPerThread, filter.approximateElementCount());
    }
}
