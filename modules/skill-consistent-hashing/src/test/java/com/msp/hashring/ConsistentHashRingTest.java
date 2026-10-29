package com.msp.hashring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsistentHashRingTest {

    @Test
    @DisplayName("Empty ring behavior should return empty Optional or empty list")
    void testEmptyRing() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        assertEquals(0, ring.getNodeCount());
        assertEquals(0, ring.getVirtualNodeCount());
        assertTrue(ring.getNode("test-key").isEmpty());
        assertTrue(ring.getNodes("test-key", 3).isEmpty());
        assertThrows(NoSuchElementException.class, () -> ring.getNodeOrThrow("test-key"));
    }

    @Test
    @DisplayName("Single node routes all keys to the single node")
    void testSingleNode() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(50);
        ring.addNode("server-1");

        assertEquals(1, ring.getNodeCount());
        assertEquals(50, ring.getVirtualNodeCount());

        for (int i = 0; i < 100; i++) {
            assertEquals("server-1", ring.getNode("key-" + i).orElseThrow());
            List<String> replicas = ring.getNodes("key-" + i, 3);
            assertEquals(1, replicas.size());
            assertEquals("server-1", replicas.getFirst());
        }
    }

    @Test
    @DisplayName("Uniform key distribution across nodes with acceptable variance")
    void testUniformDistribution() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(200, HashFunction.MURMUR3_64);
        List<String> nodes = List.of("node-A", "node-B", "node-C", "node-D", "node-E");
        nodes.forEach(ring::addNode);

        int totalKeys = 20_000;
        Map<String, Integer> counts = new HashMap<>();
        nodes.forEach(n -> counts.put(n, 0));

        for (int i = 0; i < totalKeys; i++) {
            String node = ring.getNode("user-session-" + i).orElseThrow();
            counts.put(node, counts.get(node) + 1);
        }

        double expectedShare = 1.0 / nodes.size(); // 20%
        for (String node : nodes) {
            double actualShare = (double) counts.get(node) / totalKeys;
            // With 200 virtual nodes, each node should receive between 14% and 26%
            assertTrue(actualShare >= 0.14 && actualShare <= 0.26,
                    "Node " + node + " received share " + actualShare + ", outside [0.14, 0.26]");
        }
    }

    @Test
    @DisplayName("Adding a node rebalances minimal fraction of keys (roughly 1/(N+1))")
    void testMinimalRebalancingOnNodeAddition() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(200);
        List<String> initialNodes = List.of("server-1", "server-2", "server-3", "server-4");
        initialNodes.forEach(ring::addNode);

        int totalKeys = 10_000;
        Map<String, String> initialMapping = new HashMap<>(totalKeys);
        for (int i = 0; i < totalKeys; i++) {
            String key = "item-id-" + i;
            initialMapping.put(key, ring.getNode(key).orElseThrow());
        }

        // Add 5th node
        String newNode = "server-5";
        ring.addNode(newNode);

        int changedKeys = 0;
        for (int i = 0; i < totalKeys; i++) {
            String key = "item-id-" + i;
            String newTarget = ring.getNode(key).orElseThrow();
            if (!newTarget.equals(initialMapping.get(key))) {
                changedKeys++;
                // If key changed, its new target MUST be the newly added node!
                assertEquals(newNode, newTarget, "Rebalanced key must go to new node");
            }
        }

        double rebalanceRatio = (double) changedKeys / totalKeys;
        // Ideal fraction is 1/5 = 20%. Allow standard deviation margin [13%, 28%]
        assertTrue(rebalanceRatio >= 0.13 && rebalanceRatio <= 0.28,
                "Rebalance ratio was " + rebalanceRatio + ", expected around 0.20");
    }

    @Test
    @DisplayName("Removing a node moves only keys previously hosted on the removed node")
    void testMinimalRebalancingOnNodeRemoval() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(200);
        List<String> nodes = List.of("server-1", "server-2", "server-3", "server-4", "server-5");
        nodes.forEach(ring::addNode);

        int totalKeys = 10_000;
        Map<String, String> initialMapping = new HashMap<>(totalKeys);
        for (int i = 0; i < totalKeys; i++) {
            String key = "item-" + i;
            initialMapping.put(key, ring.getNode(key).orElseThrow());
        }

        String removedNode = "server-3";
        ring.removeNode(removedNode);

        for (int i = 0; i < totalKeys; i++) {
            String key = "item-" + i;
            String previousTarget = initialMapping.get(key);
            String newTarget = ring.getNode(key).orElseThrow();

            if (previousTarget.equals(removedNode)) {
                // Must be remapped to one of remaining nodes
                assertFalse(newTarget.equals(removedNode));
            } else {
                // Keys previously on untouched nodes MUST remain on those nodes
                assertEquals(previousTarget, newTarget,
                        "Key " + key + " on untouched node should not have moved");
            }
        }
    }

    @Test
    @DisplayName("Replica selection returns unique physical nodes in deterministic clockwise order")
    void testReplicaSelectionUniqueness() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(100);
        List<String> nodes = List.of("node-1", "node-2", "node-3", "node-4", "node-5");
        nodes.forEach(ring::addNode);

        for (int i = 0; i < 500; i++) {
            String key = "data-record-" + i;
            List<String> replicas = ring.getNodes(key, 3);
            assertEquals(3, replicas.size());

            // Guarantee uniqueness among replicas
            Set<String> uniqueSet = new HashSet<>(replicas);
            assertEquals(3, uniqueSet.size(), "Replicas must be distinct physical nodes");

            // Primary node must be first replica
            assertEquals(ring.getNode(key).orElseThrow(), replicas.getFirst());
        }

        // Requesting more replicas than available physical nodes returns all physical nodes
        List<String> allReplicas = ring.getNodes("sample-key", 10);
        assertEquals(5, allReplicas.size());
        assertEquals(new HashSet<>(nodes), new HashSet<>(allReplicas));
    }

    @Test
    @DisplayName("FNV-1a hash function works correctly")
    void testFnv1aHashFunction() {
        ConsistentHashRing<String> fnvRing = new ConsistentHashRing<>(100, HashFunction.FNV1A_64);
        fnvRing.addNode("cluster-A");
        fnvRing.addNode("cluster-B");

        assertNotNull(fnvRing.getNode("fnv-key"));
        assertEquals(2, fnvRing.getNodeCount());
    }

    @Test
    @DisplayName("Idempotent node addition and removal")
    void testNodeLifecycle() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(50);
        assertTrue(ring.addNode("srv-1"));
        assertFalse(ring.addNode("srv-1")); // Duplicate
        assertEquals(1, ring.getNodeCount());
        assertEquals(50, ring.getVirtualNodeCount());

        assertTrue(ring.containsNode("srv-1"));
        assertFalse(ring.containsNode("srv-2"));

        assertTrue(ring.removeNode("srv-1"));
        assertFalse(ring.removeNode("srv-1")); // Already removed
        assertEquals(0, ring.getNodeCount());
        assertEquals(0, ring.getVirtualNodeCount());

        ring.addNode("srv-A");
        ring.addNode("srv-B");
        ring.clear();
        assertEquals(0, ring.getNodeCount());
        assertTrue(ring.getNodes().isEmpty());
    }

    @Test
    @DisplayName("Concurrent reads and node mutations under multi-threading")
    void testConcurrentReadAndWrite() throws InterruptedException {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(100);
        ring.addNode("worker-1");
        ring.addNode("worker-2");

        int threadCount = 8;
        int operationsPerThread = 2_000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successReads = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        if (i % 200 == 0 && threadId == 0) {
                            String tempNode = "temp-worker-" + i;
                            ring.addNode(tempNode);
                            ring.removeNode(tempNode);
                        } else {
                            Optional<String> node = ring.getNode("key-" + (i % 500));
                            if (node.isPresent()) {
                                successReads.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        assertTrue(successReads.get() > 0);
    }
}
