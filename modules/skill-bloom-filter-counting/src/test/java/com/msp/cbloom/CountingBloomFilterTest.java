package com.msp.cbloom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class CountingBloomFilterTest {

    @Test
    @DisplayName("Add and mightContain returns true for added elements")
    void testAddAndMightContain() {
        CountingBloomFilter filter = new CountingBloomFilter(1024, 4);

        assertFalse(filter.mightContain("user:1001"));
        assertFalse(filter.mightContain("user:1002"));

        filter.add("user:1001");
        assertTrue(filter.mightContain("user:1001"));
        assertFalse(filter.mightContain("user:1002"));
    }

    @Test
    @DisplayName("Remove element properly decrements counters and clears membership")
    void testRemoveElement() {
        CountingBloomFilter filter = new CountingBloomFilter(2048, 5);

        filter.add("session:xyz");
        assertTrue(filter.mightContain("session:xyz"));
        assertEquals(1, filter.approximateCount("session:xyz"));

        boolean removed = filter.remove("session:xyz");
        assertTrue(removed);
        assertFalse(filter.mightContain("session:xyz"));
        assertEquals(0, filter.approximateCount("session:xyz"));

        // Removing non-existent element returns false
        boolean removedAgain = filter.remove("session:xyz");
        assertFalse(removedAgain);
    }

    @Test
    @DisplayName("Multiple adds track approximate count")
    void testApproximateCount() {
        CountingBloomFilter filter = new CountingBloomFilter(4096, 4);

        filter.add("apple");
        filter.add("apple");
        filter.add("apple");

        assertTrue(filter.approximateCount("apple") >= 3);

        filter.remove("apple");
        assertTrue(filter.approximateCount("apple") >= 2);
        assertTrue(filter.mightContain("apple"));

        filter.remove("apple");
        filter.remove("apple");
        assertEquals(0, filter.approximateCount("apple"));
        assertFalse(filter.mightContain("apple"));
    }

    @Test
    @DisplayName("Clear resets all counters to zero")
    void testClear() {
        CountingBloomFilter filter = new CountingBloomFilter(1000, 3);
        filter.add("item1");
        filter.add("item2");
        filter.add("item3");

        assertTrue(filter.mightContain("item1"));
        assertTrue(filter.mightContain("item2"));

        filter.clear();

        assertFalse(filter.mightContain("item1"));
        assertFalse(filter.mightContain("item2"));
        assertFalse(filter.mightContain("item3"));
        assertEquals(0, filter.approximateCount("item1"));
    }

    @Test
    @DisplayName("Optimal factory method calculates reasonable capacity and hashCount")
    void testCreateOptimal() {
        CountingBloomFilter filter = CountingBloomFilter.create(10_000, 0.01);

        assertTrue(filter.capacity() > 10_000);
        assertTrue(filter.hashCount() >= 4);

        for (int i = 0; i < 500; i++) {
            filter.add("test-key-" + i);
        }

        for (int i = 0; i < 500; i++) {
            assertTrue(filter.mightContain("test-key-" + i));
        }
    }

    @Test
    @DisplayName("Concurrent adds and queries are thread-safe")
    void testConcurrency() throws InterruptedException {
        CountingBloomFilter filter = new CountingBloomFilter(10_000, 4);
        int threadCount = 8;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "thread-" + threadId + "-key-" + i;
                        filter.add(key);
                        assertTrue(filter.mightContain(key));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        for (int t = 0; t < threadCount; t++) {
            for (int i = 0; i < operationsPerThread; i++) {
                assertTrue(filter.mightContain("thread-" + t + "-key-" + i));
            }
        }
    }
}
