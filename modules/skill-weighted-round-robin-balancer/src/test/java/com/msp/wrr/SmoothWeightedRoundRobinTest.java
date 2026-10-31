package com.msp.wrr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SmoothWeightedRoundRobinTest {

    @Test
    @DisplayName("Nginx standard SWRR distribution pattern (A:5, B:1, C:1)")
    void testStandardNginxPattern() {
        SmoothWeightedRoundRobin<String> wrr = new SmoothWeightedRoundRobin<>();
        wrr.addServer("A", 5);
        wrr.addServer("B", 1);
        wrr.addServer("C", 1);

        // Standard sequence for weights {A:5, B:1, C:1} over 7 steps is A, A, B, A, C, A, A
        List<String> expected = List.of("A", "A", "B", "A", "C", "A", "A");
        List<String> actual = wrr.selectSequence(7);

        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Weights proportion holds precisely over multiple cycles")
    void testLongRunProportions() {
        SmoothWeightedRoundRobin<String> wrr = new SmoothWeightedRoundRobin<>();
        wrr.addServer("S1", 4);
        wrr.addServer("S2", 3);
        wrr.addServer("S3", 2);

        int totalWeight = 9;
        int cycles = 100;
        int totalRequests = totalWeight * cycles;

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < totalRequests; i++) {
            String s = wrr.select();
            counts.put(s, counts.getOrDefault(s, 0) + 1);
        }

        assertEquals(400, counts.get("S1"));
        assertEquals(300, counts.get("S2"));
        assertEquals(200, counts.get("S3"));
    }

    @Test
    @DisplayName("Dynamic add and remove of servers")
    void testDynamicMutation() {
        SmoothWeightedRoundRobin<String> wrr = new SmoothWeightedRoundRobin<>();
        wrr.addServer("A", 2);
        wrr.addServer("B", 1);
        assertEquals(2, wrr.getServerCount());
        assertEquals(3, wrr.getTotalWeight());

        // Update weight of A
        wrr.addServer("A", 4);
        assertEquals(2, wrr.getServerCount());
        assertEquals(5, wrr.getTotalWeight());

        // Remove server B
        assertTrue(wrr.removeServer("B"));
        assertEquals(1, wrr.getServerCount());
        assertEquals(4, wrr.getTotalWeight());

        // Now only A is selected
        for (int i = 0; i < 5; i++) {
            assertEquals("A", wrr.select());
        }

        assertFalse(wrr.removeServer("NON_EXISTENT"));
        assertFalse(wrr.removeServer(null));
    }

    @Test
    @DisplayName("Reset restores initial sequence")
    void testReset() {
        SmoothWeightedRoundRobin<String> wrr = new SmoothWeightedRoundRobin<>();
        wrr.addServer("A", 2);
        wrr.addServer("B", 1);

        List<String> seq1 = wrr.selectSequence(3);
        wrr.reset();
        List<String> seq2 = wrr.selectSequence(3);

        assertEquals(seq1, seq2);
    }

    @Test
    @DisplayName("Thread-safety test under concurrent load")
    void testConcurrentSelection() throws InterruptedException {
        SmoothWeightedRoundRobin<String> wrr = new SmoothWeightedRoundRobin<>();
        wrr.addServer("ALPHA", 3);
        wrr.addServer("BETA", 2);
        wrr.addServer("GAMMA", 1);

        int totalCalls = 6000;
        int threads = 12;
        int perThread = totalCalls / threads;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < perThread; i++) {
                        String s = wrr.select();
                        counts.computeIfAbsent(s, k -> new AtomicInteger()).incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // 6000 requests divided by weight 3:2:1 (total 6)
        // ALPHA: 3000, BETA: 2000, GAMMA: 1000
        assertEquals(3000, counts.get("ALPHA").get());
        assertEquals(2000, counts.get("BETA").get());
        assertEquals(1000, counts.get("GAMMA").get());
    }

    @Test
    @DisplayName("Empty balancer and input validation")
    void testEmptyAndValidation() {
        SmoothWeightedRoundRobin<String> wrr = new SmoothWeightedRoundRobin<>();
        assertTrue(wrr.isEmpty());
        assertNull(wrr.select());
        assertTrue(wrr.selectSequence(5).isEmpty());
        assertTrue(wrr.selectSequence(0).isEmpty());

        assertThrows(IllegalArgumentException.class, () -> wrr.addServer(null, 1));
        assertThrows(IllegalArgumentException.class, () -> wrr.addServer("A", 0));
        assertThrows(IllegalArgumentException.class, () -> wrr.addServer("A", -1));
    }
}
