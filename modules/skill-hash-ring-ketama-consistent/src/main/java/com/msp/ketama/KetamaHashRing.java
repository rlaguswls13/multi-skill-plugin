package com.msp.ketama;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Memcached and Nginx standard Ketama MD5 consistent hash ring implementation.
 * Produces 160 virtual points per unit weight (40 chunks x 4 virtual points).
 *
 * @param <T> Node type
 */
public final class KetamaHashRing<T> {

    private static final int POINTS_PER_HASH = 4;
    private static final int CHUNKS_PER_WEIGHT = 40; // 40 chunks * 4 = 160 virtual points

    private final TreeMap<Long, T> ring = new TreeMap<>();
    private final Map<T, Integer> nodeWeights = new LinkedHashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public KetamaHashRing() {
    }

    public KetamaHashRing(Map<T, Integer> initialNodes) {
        if (initialNodes != null) {
            for (Map.Entry<T, Integer> entry : initialNodes.entrySet()) {
                addNode(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Adds a node with default weight = 1 (160 virtual points).
     */
    public void addNode(T node) {
        addNode(node, 1);
    }

    /**
     * Adds a node with the specified weight (weight * 160 virtual points).
     *
     * @param node the node to add
     * @param weight the weight multiplier (must be > 0)
     */
    public void addNode(T node, int weight) {
        Objects.requireNonNull(node, "node must not be null");
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be greater than 0, got: " + weight);
        }

        rwLock.writeLock().lock();
        try {
            if (nodeWeights.containsKey(node)) {
                removeNodeInternal(node);
            }
            nodeWeights.put(node, weight);

            int totalChunks = weight * CHUNKS_PER_WEIGHT;
            String nodeKey = node.toString();

            for (int i = 0; i < totalChunks; i++) {
                byte[] digest = computeMd5(nodeKey + "-" + i);
                for (int h = 0; h < POINTS_PER_HASH; h++) {
                    long point = hashFromDigest(digest, h);
                    ring.put(point, node);
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Removes a node and all its virtual points from the hash ring.
     */
    public void removeNode(T node) {
        Objects.requireNonNull(node, "node must not be null");
        rwLock.writeLock().lock();
        try {
            removeNodeInternal(node);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void removeNodeInternal(T node) {
        Integer weight = nodeWeights.remove(node);
        if (weight == null) {
            return;
        }
        int totalChunks = weight * CHUNKS_PER_WEIGHT;
        String nodeKey = node.toString();

        for (int i = 0; i < totalChunks; i++) {
            byte[] digest = computeMd5(nodeKey + "-" + i);
            for (int h = 0; h < POINTS_PER_HASH; h++) {
                long point = hashFromDigest(digest, h);
                ring.remove(point);
            }
        }
    }

    /**
     * Returns the node mapped to the given key on the consistent hash ring.
     * Returns null if the ring is empty.
     */
    public T getNode(String key) {
        Objects.requireNonNull(key, "key must not be null");
        rwLock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return null;
            }

            byte[] digest = computeMd5(key);
            long hash = hashFromDigest(digest, 0);

            Long targetKey = ring.ceilingKey(hash);
            if (targetKey == null) {
                targetKey = ring.firstKey();
            }
            return ring.get(targetKey);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the number of distinct nodes registered in the ring.
     */
    public int getNodeCount() {
        rwLock.readLock().lock();
        try {
            return nodeWeights.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the total number of virtual points currently on the ring.
     */
    public int getVirtualPointCount() {
        rwLock.readLock().lock();
        try {
            return ring.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean isEmpty() {
        rwLock.readLock().lock();
        try {
            return ring.isEmpty();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Set<T> getNodes() {
        rwLock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new LinkedHashSet<>(nodeWeights.keySet()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Map<T, Integer> getNodeWeights() {
        rwLock.readLock().lock();
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(nodeWeights));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void clear() {
        rwLock.writeLock().lock();
        try {
            ring.clear();
            nodeWeights.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Ketama MD5 32-bit hash derivation formula:
     * (hash[3] & 0xFF) << 24 | (hash[2] & 0xFF) << 16 | (hash[1] & 0xFF) << 8 | (hash[0] & 0xFF)
     */
    private static long hashFromDigest(byte[] digest, int h) {
        int offset = h * 4;
        return (((long) (digest[3 + offset] & 0xFF)) << 24)
             | (((long) (digest[2 + offset] & 0xFF)) << 16)
             | (((long) (digest[1 + offset] & 0xFF)) << 8)
             | ((long) (digest[offset] & 0xFF));
    }

    private static byte[] computeMd5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}
