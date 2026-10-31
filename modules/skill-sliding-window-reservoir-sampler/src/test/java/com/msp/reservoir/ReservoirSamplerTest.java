package com.msp.reservoir;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ReservoirSamplerTest {

    @Test
    @DisplayName("Sample size stays <= k when fewer than k elements arrive")
    void testFewerThanKItems() {
        ReservoirSampler<String> sampler = new ReservoirSampler<>(5);
        sampler.sample("apple");
        sampler.sample("banana");
        sampler.sample("cherry");

        assertEquals(3, sampler.totalSeenCount());
        List<String> samples = sampler.getSamples();
        assertEquals(3, samples.size());
        assertEquals(List.of("apple", "banana", "cherry"), samples);
    }

    @Test
    @DisplayName("Sample size stays exactly k when totalSeen > k")
    void testExceedingKItems() {
        ReservoirSampler<Integer> sampler = new ReservoirSampler<>(5);
        for (int i = 0; i < 100; i++) {
            sampler.sample(i);
        }

        assertEquals(100, sampler.totalSeenCount());
        List<Integer> samples = sampler.getSamples();
        assertEquals(5, samples.size());

        // Samples must all be distinct (no duplicates added in algorithm R)
        Set<Integer> uniqueSamples = new HashSet<>(samples);
        assertEquals(5, uniqueSamples.size());
    }

    @Test
    @DisplayName("Clear resets samples and total seen count")
    void testClear() {
        ReservoirSampler<Integer> sampler = new ReservoirSampler<>(3);
        sampler.sample(1);
        sampler.sample(2);
        sampler.sample(3);
        sampler.sample(4);

        assertEquals(4, sampler.totalSeenCount());
        assertEquals(3, sampler.getSamples().size());

        sampler.clear();
        assertEquals(0, sampler.totalSeenCount());
        assertTrue(sampler.getSamples().isEmpty());
    }

    @Test
    @DisplayName("Invalid capacity throws IllegalArgumentException")
    void testInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new ReservoirSampler<>(0));
        assertThrows(IllegalArgumentException.class, () -> new ReservoirSampler<>(-5));
        assertThrows(NullPointerException.class, () -> new ReservoirSampler<>(5, null));
    }

    @Test
    @DisplayName("Deterministic test with fixed seed Random")
    void testDeterministicSampling() {
        Random r1 = new Random(42L);
        Random r2 = new Random(42L);

        ReservoirSampler<Integer> s1 = new ReservoirSampler<>(5, r1);
        ReservoirSampler<Integer> s2 = new ReservoirSampler<>(5, r2);

        for (int i = 0; i < 50; i++) {
            s1.sample(i);
            s2.sample(i);
        }

        assertEquals(s1.getSamples(), s2.getSamples());
    }

    @Test
    @DisplayName("Returned samples list is immutable")
    void testImmutableSamples() {
        ReservoirSampler<String> sampler = new ReservoirSampler<>(2);
        sampler.sample("x");
        List<String> samples = sampler.getSamples();

        assertThrows(UnsupportedOperationException.class, () -> samples.add("y"));
    }

    @Test
    @DisplayName("Thread safety under concurrent sampling")
    void testConcurrentSampling() throws InterruptedException {
        int threadCount = 10;
        int itemsPerThread = 500;
        ReservoirSampler<Integer> sampler = new ReservoirSampler<>(20);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            CountDownLatch latch = new CountDownLatch(threadCount);
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < itemsPerThread; i++) {
                            sampler.sample(threadId * 10000 + i);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        assertEquals(threadCount * itemsPerThread, sampler.totalSeenCount());
        assertEquals(20, sampler.getSamples().size());
    }
}
