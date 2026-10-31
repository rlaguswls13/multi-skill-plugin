package com.msp.ketama;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class KetamaHashRingTest {

    @Test
    @DisplayName("Empty ring returns null and 0 node count")
    void testEmptyRing() {
        KetamaHashRing<String> ring = new KetamaHashRing<>();
        assertTrue(ring.isEmpty());
        assertEquals(0, ring.getNodeCount());
        assertEquals(0, ring.getVirtualPointCount());
        assertNull(ring.getNode("sample-key"));
    }

    @Test
    @DisplayName("Virtual points count conforms to 160 points per unit weight")
    void testVirtualPointsCount() {
        KetamaHashRing<String> ring = new KetamaHashRing<>();
        ring.addNode("server-1", 1);
        assertEquals(1, ring.getNodeCount());
        assertEquals(160, ring.getVirtualPointCount());

        ring.addNode("server-2", 2);
        assertEquals(2, ring.getNodeCount());
        assertEquals(160 + 320, ring.getVirtualPointCount());

        ring.removeNode("server-1");
        assertEquals(1, ring.getNodeCount());
        assertEquals(320, ring.getVirtualPointCount());
    }

    @Test
    @DisplayName("Uniform key distribution across multiple nodes")
    void testUniformKeyDistribution() {
        KetamaHashRing<String> ring = new KetamaHashRing<>();
        ring.addNode("node-a", 1);
        ring.addNode("node-b", 1);
        ring.addNode("node-c", 1);

        int totalKeys = 10_000;
        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < totalKeys; i++) {
            String key = "session-key-uuid-" + i;
            String node = ring.getNode(key);
            assertNotNull(node);
            counts.merge(node, 1, Integer::sum);
        }

        // Each node should receive roughly 33.3% of keys (within 25% to 42% range)
        for (String node : List.of("node-a", "node-b", "node-c")) {
            int count = counts.getOrDefault(node, 0);
            double ratio = (double) count / totalKeys;
            assertTrue(ratio >= 0.25 && ratio <= 0.42,
                    "Expected node " + node + " ratio ~0.33, but got " + ratio);
        }
    }

    @Test
    @DisplayName("Minimal remapping on node addition")
    void testMinimalRemappingOnAddNode() {
        KetamaHashRing<String> ring = new KetamaHashRing<>();
        ring.addNode("cache-1", 1);
        ring.addNode("cache-2", 1);
        ring.addNode("cache-3", 1);

        int totalKeys = 10_000;
        Map<String, String> initialMapping = new HashMap<>();
        for (int i = 0; i < totalKeys; i++) {
            String key = "cache-key-" + i;
            initialMapping.put(key, ring.getNode(key));
        }

        // Add 4th node
        ring.addNode("cache-4", 1);

        int remappedKeys = 0;
        for (int i = 0; i < totalKeys; i++) {
            String key = "cache-key-" + i;
            String newNode = ring.getNode(key);
            if (!newNode.equals(initialMapping.get(key))) {
                remappedKeys++;
                // Keys that moved should have moved to the newly added node cache-4
                assertEquals("cache-4", newNode);
            }
        }

        // With consistent hashing, roughly 1/4 (25%) of keys should be remapped to the new node
        double remappedRatio = (double) remappedKeys / totalKeys;
        assertTrue(remappedRatio >= 0.18 && remappedRatio <= 0.32,
                "Expected ~25% remapped keys, but got " + (remappedRatio * 100) + "%");
    }

    @Test
    @DisplayName("Weight proportional distribution")
    void testWeightProportionalDistribution() {
        KetamaHashRing<String> ring = new KetamaHashRing<>();
        ring.addNode("low-spec-node", 1);
        ring.addNode("high-spec-node", 2);

        int totalKeys = 10_000;
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < totalKeys; i++) {
            String key = "heavy-key-" + i;
            String node = ring.getNode(key);
            counts.merge(node, 1, Integer::sum);
        }

        int lowCount = counts.getOrDefault("low-spec-node", 0);
        int highCount = counts.getOrDefault("high-spec-node", 0);

        // highCount should be approximately 2x lowCount (e.g. 66% vs 33%)
        double ratio = (double) highCount / (lowCount + highCount);
        assertTrue(ratio >= 0.58 && ratio <= 0.75,
                "Expected high spec node ratio ~0.66, but got " + ratio);
    }

    @Test
    @DisplayName("Invalid weight argument validation")
    void testInvalidWeightThrows() {
        KetamaHashRing<String> ring = new KetamaHashRing<>();
        assertThrows(IllegalArgumentException.class, () -> ring.addNode("server-bad", 0));
        assertThrows(IllegalArgumentException.class, () -> ring.addNode("server-bad", -5));
        assertThrows(NullPointerException.class, () -> ring.addNode(null, 1));
        assertThrows(NullPointerException.class, () -> ring.getNode(null));
    }
}
