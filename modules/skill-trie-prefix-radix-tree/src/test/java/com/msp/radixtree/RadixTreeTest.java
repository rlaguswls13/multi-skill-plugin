package com.msp.radixtree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class RadixTreeTest {

    @Test
    @DisplayName("Should put, get, check existence, and overwrite values properly")
    void testBasicPutAndGet() {
        RadixTree<Integer> tree = new RadixTree<>();
        assertTrue(tree.isEmpty());
        assertEquals(0, tree.size());

        tree.put("apple", 100);
        tree.put("app", 200);
        tree.put("application", 300);

        assertEquals(3, tree.size());
        assertTrue(tree.containsKey("apple"));
        assertTrue(tree.containsKey("app"));
        assertTrue(tree.containsKey("application"));
        assertFalse(tree.containsKey("appl"));
        assertFalse(tree.containsKey("banana"));

        assertEquals(100, tree.get("apple"));
        assertEquals(200, tree.get("app"));
        assertEquals(300, tree.get("application"));
        assertNull(tree.get("orange"));

        // Overwrite
        tree.put("apple", 999);
        assertEquals(3, tree.size());
        assertEquals(999, tree.get("apple"));
    }

    @Test
    @DisplayName("Should handle empty string root key correctly")
    void testRootEmptyKey() {
        RadixTree<String> tree = new RadixTree<>();
        assertNull(tree.get(""));
        assertFalse(tree.containsKey(""));

        tree.put("", "ROOT");
        assertEquals(1, tree.size());
        assertTrue(tree.containsKey(""));
        assertEquals("ROOT", tree.get(""));

        tree.put("child", "CHILD");
        assertEquals(2, tree.size());

        assertEquals("ROOT", tree.remove(""));
        assertEquals(1, tree.size());
        assertFalse(tree.containsKey(""));
        assertEquals("CHILD", tree.get("child"));
    }

    @Test
    @DisplayName("Should perform edge splitting on common prefixes")
    void testEdgeSplitting() {
        RadixTree<String> tree = new RadixTree<>();
        tree.put("romane", "I");
        tree.put("romanus", "II");
        tree.put("romanesque", "III");
        tree.put("roman", "IV");
        tree.put("rubens", "V");
        tree.put("ruber", "VI");
        tree.put("rubicon", "VII");
        tree.put("rubicundus", "VIII");

        assertEquals(8, tree.size());
        assertEquals("I", tree.get("romane"));
        assertEquals("II", tree.get("romanus"));
        assertEquals("III", tree.get("romanesque"));
        assertEquals("IV", tree.get("roman"));
        assertEquals("V", tree.get("rubens"));
        assertEquals("VI", tree.get("ruber"));
        assertEquals("VII", tree.get("rubicon"));
        assertEquals("VIII", tree.get("rubicundus"));
    }

    @Test
    @DisplayName("Should perform node merging on removal")
    void testNodeMergingOnRemove() {
        RadixTree<String> tree = new RadixTree<>();
        tree.put("slow", "1");
        tree.put("slower", "2");

        assertEquals(2, tree.size());
        assertEquals("2", tree.remove("slower"));
        assertEquals(1, tree.size());
        assertEquals("1", tree.get("slow"));
        assertNull(tree.get("slower"));

        // Add a branch and remove the middle node
        tree.put("slower", "2");
        tree.put("slowly", "3");
        assertEquals(3, tree.size());

        assertEquals("1", tree.remove("slow"));
        assertEquals(2, tree.size());
        assertEquals("2", tree.get("slower"));
        assertEquals("3", tree.get("slowly"));

        assertEquals("2", tree.remove("slower"));
        assertEquals(1, tree.size());
        assertEquals("3", tree.get("slowly"));

        assertEquals("3", tree.remove("slowly"));
        assertEquals(0, tree.size());
        assertTrue(tree.isEmpty());
    }

    @Test
    @DisplayName("Should search by prefix and return matching values")
    void testSearchByPrefix() {
        RadixTree<String> tree = new RadixTree<>();
        tree.put("banana", "b1");
        tree.put("bandana", "b2");
        tree.put("banner", "b3");
        tree.put("apple", "a1");

        List<String> banMatches = tree.searchByPrefix("ban");
        assertEquals(3, banMatches.size());
        assertTrue(banMatches.contains("b1"));
        assertTrue(banMatches.contains("b2"));
        assertTrue(banMatches.contains("b3"));

        List<String> bannerMatches = tree.searchByPrefix("banner");
        assertEquals(1, bannerMatches.size());
        assertEquals("b3", bannerMatches.getFirst());

        List<String> noMatches = tree.searchByPrefix("cat");
        assertTrue(noMatches.isEmpty());

        List<String> allMatches = tree.searchByPrefix("");
        assertEquals(4, allMatches.size());
    }

    @Test
    @DisplayName("Should return entries with prefix with correct keys")
    void testEntriesWithPrefix() {
        RadixTree<Integer> tree = new RadixTree<>();
        tree.put("/users", 1);
        tree.put("/users/profile", 2);
        tree.put("/users/settings", 3);
        tree.put("/posts", 4);

        List<Map.Entry<String, Integer>> entries = tree.entriesWithPrefix("/users");
        assertEquals(3, entries.size());

        Map<String, Integer> map = Map.of(
                entries.get(0).getKey(), entries.get(0).getValue(),
                entries.get(1).getKey(), entries.get(1).getValue(),
                entries.get(2).getKey(), entries.get(2).getValue()
        );

        assertEquals(1, map.get("/users"));
        assertEquals(2, map.get("/users/profile"));
        assertEquals(3, map.get("/users/settings"));
    }

    @Test
    @DisplayName("Should find longest prefix of a given key")
    void testLongestPrefixOf() {
        RadixTree<String> tree = new RadixTree<>();
        tree.put("/api", "root_api");
        tree.put("/api/v1", "v1");
        tree.put("/api/v1/orders", "orders");

        assertEquals("/api/v1/orders", tree.longestPrefixOf("/api/v1/orders/12345/items"));
        assertEquals("/api/v1", tree.longestPrefixOf("/api/v1/customers/99"));
        assertEquals("/api", tree.longestPrefixOf("/api/unknown"));
        assertNull(tree.longestPrefixOf("/internal/metrics"));

        // If root key "" is present
        tree.put("", "default");
        assertEquals("", tree.longestPrefixOf("/internal/metrics"));
    }

    @Test
    @DisplayName("Should support clear and boundary validations")
    void testClearAndNullGuards() {
        RadixTree<String> tree = new RadixTree<>();
        tree.put("foo", "bar");
        tree.put("baz", "qux");
        assertEquals(2, tree.size());

        tree.clear();
        assertEquals(0, tree.size());
        assertTrue(tree.isEmpty());
        assertNull(tree.get("foo"));

        assertThrows(NullPointerException.class, () -> tree.put(null, "val"));
        assertThrows(NullPointerException.class, () -> tree.get(null));
        assertThrows(NullPointerException.class, () -> tree.remove(null));
        assertThrows(NullPointerException.class, () -> tree.searchByPrefix(null));
        assertThrows(NullPointerException.class, () -> tree.longestPrefixOf(null));
    }

    @Test
    @DisplayName("Should support concurrent multithreaded reads and writes safely")
    void testConcurrentAccess() throws InterruptedException {
        RadixTree<Integer> tree = new RadixTree<>();
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
                        tree.put(key, i);
                        assertEquals(i, tree.get(key));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        assertEquals(threadCount * operationsPerThread, tree.size());
    }
}
