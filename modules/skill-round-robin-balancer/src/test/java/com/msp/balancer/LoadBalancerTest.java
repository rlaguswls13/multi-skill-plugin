package com.msp.balancer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LoadBalancerTest {

    @Nested
    @DisplayName("RoundRobinBalancer Tests")
    class RoundRobinTests {

        @Test
        @DisplayName("Cyclic round-robin ordering across nodes")
        void testCyclicOrder() {
            RoundRobinBalancer<String> balancer = new RoundRobinBalancer<>(List.of("node1", "node2", "node3"));

            assertEquals(3, balancer.size());
            assertFalse(balancer.isEmpty());

            assertEquals("node1", balancer.next());
            assertEquals("node2", balancer.next());
            assertEquals("node3", balancer.next());
            assertEquals("node1", balancer.next());
            assertEquals("node2", balancer.next());
            assertEquals("node3", balancer.next());
        }

        @Test
        @DisplayName("Empty balancer error handling")
        void testEmptyBalancer() {
            RoundRobinBalancer<String> balancer = new RoundRobinBalancer<>();
            assertTrue(balancer.isEmpty());
            assertEquals(0, balancer.size());

            assertThrows(NoSuchElementException.class, balancer::next);
            assertEquals(Optional.empty(), balancer.tryNext());
        }

        @Test
        @DisplayName("Dynamic node addition and removal")
        void testDynamicModification() {
            RoundRobinBalancer<String> balancer = new RoundRobinBalancer<>();
            balancer.addNode("A").addNode("B");

            assertEquals("A", balancer.next());
            assertEquals("B", balancer.next());

            balancer.addNode("C");
            assertEquals("C", balancer.next());

            assertTrue(balancer.removeNode("B"));
            assertFalse(balancer.removeNode("NonExistent"));

            assertEquals(2, balancer.size());
        }

        @Test
        @DisplayName("Concurrent round-robin balancing across multiple threads")
        void testConcurrentRoundRobin() throws InterruptedException {
            RoundRobinBalancer<String> balancer = new RoundRobinBalancer<>(List.of("srv1", "srv2", "srv3", "srv4"));

            int threads = 8;
            int requestsPerThread = 2500;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
            counts.put("srv1", new AtomicInteger());
            counts.put("srv2", new AtomicInteger());
            counts.put("srv3", new AtomicInteger());
            counts.put("srv4", new AtomicInteger());

            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < requestsPerThread; i++) {
                            String node = balancer.next();
                            counts.get(node).incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            int total = threads * requestsPerThread; // 20,000
            for (AtomicInteger count : counts.values()) {
                // Each server receives exactly 1/4th of requests (5,000)
                assertEquals(total / 4, count.get());
            }
        }
    }

    @Nested
    @DisplayName("WeightedRoundRobinBalancer Tests")
    class WeightedRoundRobinTests {

        @Test
        @DisplayName("Weighted counts conform to defined weights over full cycle")
        void testWeightedRatios() {
            WeightedRoundRobinBalancer<String> balancer = new WeightedRoundRobinBalancer<>();
            balancer.addNode("NodeA", 4);
            balancer.addNode("NodeB", 2);

            Map<String, Integer> counts = new HashMap<>();
            counts.put("NodeA", 0);
            counts.put("NodeB", 0);

            // Cycle of 6 requests: 4 to NodeA, 2 to NodeB
            for (int i = 0; i < 6; i++) {
                String node = balancer.next();
                counts.put(node, counts.get(node) + 1);
            }

            assertEquals(4, counts.get("NodeA"));
            assertEquals(2, counts.get("NodeB"));
        }

        @Test
        @DisplayName("Invalid weights are rejected")
        void testInvalidWeight() {
            WeightedRoundRobinBalancer<String> balancer = new WeightedRoundRobinBalancer<>();
            assertThrows(IllegalArgumentException.class, () -> balancer.addNode("A", 0));
            assertThrows(IllegalArgumentException.class, () -> balancer.addNode("A", -3));
        }

        @Test
        @DisplayName("Removal of weighted nodes recalculates parameters")
        void testRemoval() {
            WeightedRoundRobinBalancer<String> balancer = new WeightedRoundRobinBalancer<>();
            balancer.addNode("A", 5);
            balancer.addNode("B", 10);

            assertTrue(balancer.removeNode("A"));
            assertEquals(1, balancer.size());
            assertEquals("B", balancer.next());
        }
    }

    @Nested
    @DisplayName("SmoothWeightedRoundRobinBalancer Tests")
    class SmoothWeightedRoundRobinTests {

        @Test
        @DisplayName("Smooth interleaving matches Nginx exact sequence without clustering")
        void testSmoothInterleavingSequence() {
            // Weights: A:5, B:1, C:1
            // Total weight: 7
            SmoothWeightedRoundRobinBalancer<String> balancer = new SmoothWeightedRoundRobinBalancer<>();
            balancer.addNode("A", 5);
            balancer.addNode("B", 1);
            balancer.addNode("C", 1);

            List<String> sequence = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                sequence.add(balancer.next());
            }

            // Expected exact sequence: A, A, B, A, C, A, A
            List<String> expected = List.of("A", "A", "B", "A", "C", "A", "A");
            assertEquals(expected, sequence);

            // Verify cycle repeats identically
            List<String> secondCycle = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                secondCycle.add(balancer.next());
            }
            assertEquals(expected, secondCycle);
        }

        @Test
        @DisplayName("Proportional distribution over 7,000 requests")
        void testSmoothDistributionCounts() {
            SmoothWeightedRoundRobinBalancer<String> balancer = new SmoothWeightedRoundRobinBalancer<>();
            balancer.addNode("Server1", 7);
            balancer.addNode("Server2", 2);
            balancer.addNode("Server3", 1);

            Map<String, Integer> counts = new HashMap<>();
            counts.put("Server1", 0);
            counts.put("Server2", 0);
            counts.put("Server3", 0);

            int total = 10_000;
            for (int i = 0; i < total; i++) {
                String node = balancer.next();
                counts.put(node, counts.get(node) + 1);
            }

            assertEquals(7000, counts.get("Server1"));
            assertEquals(2000, counts.get("Server2"));
            assertEquals(1000, counts.get("Server3"));
        }

        @Test
        @DisplayName("Concurrent thread safety for smooth weighted round robin")
        void testConcurrentSmooth() throws InterruptedException {
            SmoothWeightedRoundRobinBalancer<String> balancer = new SmoothWeightedRoundRobinBalancer<>();
            balancer.addNode("Fast", 3);
            balancer.addNode("Slow", 1);

            int threads = 10;
            int requestsPerThread = 400; // total 4000 (multiple of 4)
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
            counts.put("Fast", new AtomicInteger());
            counts.put("Slow", new AtomicInteger());

            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < requestsPerThread; i++) {
                            String node = balancer.next();
                            counts.get(node).incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertEquals(3000, counts.get("Fast").get());
            assertEquals(1000, counts.get("Slow").get());
        }
    }
}
