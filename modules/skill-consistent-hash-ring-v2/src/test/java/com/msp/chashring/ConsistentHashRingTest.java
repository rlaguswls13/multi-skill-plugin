package com.msp.chashring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashRingTest {

    @Test
    @DisplayName("빈 링 동작 검증: null 및 빈 리스트 반환")
    void testEmptyRing() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        assertEquals(0, ring.nodeCount());
        assertEquals(0, ring.virtualNodeCount());
        assertNull(ring.getNode("test-key"));
        assertTrue(ring.getNodes("test-key", 3).isEmpty());
        assertFalse(ring.removeNode("non-existent"));
    }

    @Test
    @DisplayName("단일 노드 등록 및 라우팅 검증")
    void testSingleNode() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(50);
        ring.addNode("server-01");

        assertEquals(1, ring.nodeCount());
        assertEquals(50, ring.virtualNodeCount());
        assertEquals("server-01", ring.getNode("key-1"));
        assertEquals("server-01", ring.getNode("key-2"));

        List<String> replicas = ring.getNodes("key-1", 3);
        assertEquals(1, replicas.size());
        assertEquals("server-01", replicas.getFirst());
    }

    @Test
    @DisplayName("가중치에 따른 가상 노드 수 및 키 분산 비율 검증")
    void testWeightsAndVirtualNodes() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(100);
        ring.addNode("server-low", 1);   // 100 vnodes
        ring.addNode("server-high", 3);  // 300 vnodes

        assertEquals(2, ring.nodeCount());
        assertEquals(400, ring.virtualNodeCount());
        assertEquals(1, ring.getWeight("server-low"));
        assertEquals(3, ring.getWeight("server-high"));

        // 1000개 키 분산 시 server-high가 server-low보다 확실히 많은 키를 담당해야 함
        Map<String, Integer> distribution = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String node = ring.getNode("key-" + i);
            distribution.merge(node, 1, Integer::sum);
        }

        int countLow = distribution.getOrDefault("server-low", 0);
        int countHigh = distribution.getOrDefault("server-high", 0);

        assertTrue(countHigh > countLow, "server-high should have more keys than server-low: high=" + countHigh + ", low=" + countLow);
        assertTrue(countHigh > 600, "server-high should handle majority of keys with 3x weight: " + countHigh);
    }

    @Test
    @DisplayName("노드 제거 시 정상적인 virtual node 정리 및 재배치 검증")
    void testNodeRemoval() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(50);
        ring.addNode("node-A");
        ring.addNode("node-B");

        assertEquals(2, ring.nodeCount());
        assertEquals(100, ring.virtualNodeCount());

        boolean removed = ring.removeNode("node-A");
        assertTrue(removed);
        assertEquals(1, ring.nodeCount());
        assertEquals(50, ring.virtualNodeCount());
        assertFalse(ring.getRegisteredNodes().contains("node-A"));
        assertTrue(ring.getRegisteredNodes().contains("node-B"));

        // 모든 키는 이제 node-B로 라우팅되어야 함
        for (int i = 0; i < 20; i++) {
            assertEquals("node-B", ring.getNode("test-" + i));
        }
    }

    @Test
    @DisplayName("복제 노드 N개 선택 검증 (중복 없음 및 경계값 처리)")
    void testGetNodesReplicas() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(50);
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        // 2개 복제본 요청
        List<String> replicas2 = ring.getNodes("sample-key", 2);
        assertEquals(2, replicas2.size());
        assertEquals(2, new HashSet<>(replicas2).size(), "Replicas must be distinct");

        // 물리 노드 수(3)보다 많은 5개 요청 시 최대 3개만 반환
        List<String> replicasAll = ring.getNodes("sample-key", 5);
        assertEquals(3, replicasAll.size());
        assertEquals(3, new HashSet<>(replicasAll).size());

        // 0 이하 count 요청 시 빈 리스트 반환
        assertTrue(ring.getNodes("sample-key", 0).isEmpty());
        assertTrue(ring.getNodes("sample-key", -1).isEmpty());
        assertTrue(ring.getNodes(null, 2).isEmpty());
    }

    @Test
    @DisplayName("노드 재추가(업데이트) 시 가중치 및 vnodes 갱신 검증")
    void testNodeUpdate() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(50);
        ring.addNode("node-A", 1); // 50 vnodes
        assertEquals(50, ring.virtualNodeCount());

        ring.addNode("node-A", 3); // Update to 150 vnodes
        assertEquals(1, ring.nodeCount());
        assertEquals(150, ring.virtualNodeCount());
        assertEquals(3, ring.getWeight("node-A"));
    }

    @Test
    @DisplayName("동시성 멀티스레드 읽기/쓰기 안전성 검증")
    void testConcurrency() throws Exception {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(30);
        for (int i = 0; i < 5; i++) {
            ring.addNode("node-" + i);
        }

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
                        String node = ring.getNode(key);
                        assertNotNull(node);

                        if (i % 50 == 0) {
                            ring.addNode("temp-" + threadId, 1);
                        } else if (i % 50 == 25) {
                            ring.removeNode("temp-" + threadId);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(completed, "Concurrency test timed out");
        assertTrue(ring.nodeCount() >= 5);
    }
}
