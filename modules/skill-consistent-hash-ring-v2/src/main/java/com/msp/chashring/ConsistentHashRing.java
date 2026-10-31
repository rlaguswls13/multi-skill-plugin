package com.msp.chashring;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe Consistent Hash Ring implementation with virtual nodes and weight support.
 * Suitable for distributed caching, database sharding, and consistent load balancing.
 *
 * @param <T> physical node type
 */
public class ConsistentHashRing<T> {

    public static final int DEFAULT_VNODES_PER_WEIGHT = 100;

    private final int vnodesPerWeight;
    private final HashFunction hashFunction;
    private final NavigableMap<Long, T> ring = new TreeMap<>();
    private final Map<T, Integer> nodeWeights = new HashMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * Creates a new ConsistentHashRing with default 100 virtual nodes per weight
     * and SHA-256 hash function.
     */
    public ConsistentHashRing() {
        this(DEFAULT_VNODES_PER_WEIGHT, Sha256HashFunction.INSTANCE);
    }

    /**
     * Creates a new ConsistentHashRing with specified virtual nodes per weight.
     *
     * @param vnodesPerWeight number of virtual nodes assigned per 1 unit of weight
     */
    public ConsistentHashRing(int vnodesPerWeight) {
        this(vnodesPerWeight, Sha256HashFunction.INSTANCE);
    }

    /**
     * Creates a new ConsistentHashRing with specified virtual nodes and hash function.
     *
     * @param vnodesPerWeight number of virtual nodes assigned per 1 unit of weight
     * @param hashFunction    hash calculation function
     */
    public ConsistentHashRing(int vnodesPerWeight, HashFunction hashFunction) {
        if (vnodesPerWeight <= 0) {
            throw new IllegalArgumentException("vnodesPerWeight must be greater than 0: " + vnodesPerWeight);
        }
        this.vnodesPerWeight = vnodesPerWeight;
        this.hashFunction = Objects.requireNonNull(hashFunction, "hashFunction must not be null");
    }

    /**
     * Adds a physical node to the ring with default weight of 1.
     *
     * @param node physical node
     */
    public void addNode(T node) {
        addNode(node, 1);
    }

    /**
     * Adds or updates a physical node with the given positive weight.
     * Higher weight allocates proportionally more virtual nodes on the ring.
     *
     * @param node   physical node
     * @param weight positive weight multiplier (e.g. 1, 2, 5)
     */
    public void addNode(T node, int weight) {
        Objects.requireNonNull(node, "node must not be null");
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be greater than 0: " + weight);
        }

        rwLock.writeLock().lock();
        try {
            if (nodeWeights.containsKey(node)) {
                removeNodeInternal(node);
            }

            nodeWeights.put(node, weight);
            int totalVNodes = weight * vnodesPerWeight;
            String nodeKey = node.toString();

            for (int i = 0; i < totalVNodes; i++) {
                long hash = hashFunction.hash(nodeKey + "#VN#" + i);
                // Handle potential hash collisions by probing forward
                while (ring.containsKey(hash)) {
                    hash++;
                }
                ring.put(hash, node);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Removes a physical node and all its associated virtual nodes from the ring.
     *
     * @param node physical node to remove
     * @return true if the node was present and removed, false otherwise
     */
    public boolean removeNode(T node) {
        if (node == null) {
            return false;
        }

        rwLock.writeLock().lock();
        try {
            if (!nodeWeights.containsKey(node)) {
                return false;
            }
            removeNodeInternal(node);
            return true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void removeNodeInternal(T node) {
        nodeWeights.remove(node);
        ring.values().removeIf(n -> Objects.equals(n, node));
    }

    /**
     * Finds the primary node responsible for the given key.
     *
     * @param key routing key
     * @return physical node, or null if the ring has no nodes
     */
    public T getNode(String key) {
        if (key == null) {
            return null;
        }

        rwLock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return null;
            }

            long hash = hashFunction.hash(key);
            Map.Entry<Long, T> entry = ring.ceilingEntry(hash);
            if (entry == null) {
                entry = ring.firstEntry();
            }
            return entry != null ? entry.getValue() : null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Finds up to {@code count} distinct replica nodes responsible for the key,
     * walking clockwise around the ring.
     *
     * @param key   routing key
     * @param count maximum number of distinct replica nodes to return
     * @return list of distinct physical nodes in clockwise ring order
     */
    public List<T> getNodes(String key, int count) {
        if (key == null || count <= 0) {
            return List.of();
        }

        rwLock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return List.of();
            }

            int targetCount = Math.min(count, nodeWeights.size());
            Set<T> distinctNodes = new LinkedHashSet<>(targetCount);
            long hash = hashFunction.hash(key);

            // Clockwise from hash to end of ring
            for (T node : ring.tailMap(hash, true).values()) {
                distinctNodes.add(node);
                if (distinctNodes.size() == targetCount) {
                    return new ArrayList<>(distinctNodes);
                }
            }

            // Wrap around clockwise from start of ring up to hash
            for (T node : ring.headMap(hash, false).values()) {
                distinctNodes.add(node);
                if (distinctNodes.size() == targetCount) {
                    return new ArrayList<>(distinctNodes);
                }
            }

            return new ArrayList<>(distinctNodes);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the number of distinct physical nodes registered in the ring.
     *
     * @return physical node count
     */
    public int nodeCount() {
        rwLock.readLock().lock();
        try {
            return nodeWeights.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the total number of virtual nodes currently placed on the ring.
     *
     * @return virtual node count
     */
    public int virtualNodeCount() {
        rwLock.readLock().lock();
        try {
            return ring.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns an unmodifiable set of all registered physical nodes.
     *
     * @return set of physical nodes
     */
    public Set<T> getRegisteredNodes() {
        rwLock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new HashSet<>(nodeWeights.keySet()));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the configured weight of a given physical node.
     *
     * @param node physical node
     * @return weight, or 0 if node is not in ring
     */
    public int getWeight(T node) {
        rwLock.readLock().lock();
        try {
            return nodeWeights.getOrDefault(node, 0);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Clears all nodes from the ring.
     */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            ring.clear();
            nodeWeights.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
