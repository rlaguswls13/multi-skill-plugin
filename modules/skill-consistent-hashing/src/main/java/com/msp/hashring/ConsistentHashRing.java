package com.msp.hashring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;

/**
 * Thread-safe consistent hash ring implementation with virtual nodes.
 * <p>
 * Distributes keys uniformly across physical nodes while minimizing data rebalancing
 * when nodes are dynamically added or removed (rebalancing fraction ~ 1/N).
 *
 * @param <T> the physical node type
 */
public class ConsistentHashRing<T> {

    public static final int DEFAULT_VIRTUAL_NODES = 150;

    private final int virtualNodesPerNode;
    private final HashFunction hashFunction;
    private final Function<T, String> nodeKeyMapper;

    private final ConcurrentSkipListMap<Long, T> ring = new ConcurrentSkipListMap<>();
    private final Set<T> physicalNodes = ConcurrentHashMap.newKeySet();

    /**
     * Constructs a consistent hash ring with default settings (150 virtual nodes, Murmur3_64, Object::toString).
     */
    public ConsistentHashRing() {
        this(DEFAULT_VIRTUAL_NODES, HashFunction.MURMUR3_64, Objects::toString);
    }

    /**
     * Constructs a consistent hash ring with specified virtual nodes per physical node.
     *
     * @param virtualNodesPerNode number of virtual nodes for each physical node (must be >= 1)
     */
    public ConsistentHashRing(int virtualNodesPerNode) {
        this(virtualNodesPerNode, HashFunction.MURMUR3_64, Objects::toString);
    }

    /**
     * Constructs a consistent hash ring with specified virtual nodes and hash function.
     *
     * @param virtualNodesPerNode number of virtual nodes for each physical node
     * @param hashFunction        64-bit hash function
     */
    public ConsistentHashRing(int virtualNodesPerNode, HashFunction hashFunction) {
        this(virtualNodesPerNode, hashFunction, Objects::toString);
    }

    /**
     * Constructs a consistent hash ring with full configuration.
     *
     * @param virtualNodesPerNode number of virtual nodes for each physical node
     * @param hashFunction        64-bit hash function
     * @param nodeKeyMapper       function to convert a physical node into its base string identifier
     */
    public ConsistentHashRing(int virtualNodesPerNode, HashFunction hashFunction, Function<T, String> nodeKeyMapper) {
        if (virtualNodesPerNode < 1) {
            throw new IllegalArgumentException("virtualNodesPerNode must be >= 1, got: " + virtualNodesPerNode);
        }
        this.virtualNodesPerNode = virtualNodesPerNode;
        this.hashFunction = Objects.requireNonNull(hashFunction, "hashFunction cannot be null");
        this.nodeKeyMapper = Objects.requireNonNull(nodeKeyMapper, "nodeKeyMapper cannot be null");
    }

    /**
     * Adds a physical node to the ring by registering its virtual nodes.
     *
     * @param node the physical node to add
     * @return true if the node was newly added, false if it was already present
     */
    public boolean addNode(T node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!physicalNodes.add(node)) {
            return false;
        }

        String baseKey = nodeKeyMapper.apply(node);
        for (int i = 0; i < virtualNodesPerNode; i++) {
            long hash = hashFunction.hash(baseKey + "#VN#" + i);
            ring.put(hash, node);
        }
        return true;
    }

    /**
     * Removes a physical node and all its virtual nodes from the ring.
     *
     * @param node the physical node to remove
     * @return true if the node was removed, false if it was not found
     */
    public boolean removeNode(T node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!physicalNodes.remove(node)) {
            return false;
        }

        String baseKey = nodeKeyMapper.apply(node);
        for (int i = 0; i < virtualNodesPerNode; i++) {
            long hash = hashFunction.hash(baseKey + "#VN#" + i);
            ring.remove(hash, node);
        }
        return true;
    }

    /**
     * Finds the primary node responsible for the given key.
     *
     * @param key the lookup key
     * @return Optional containing the target node, or empty if ring has no nodes
     */
    public Optional<T> getNode(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        if (ring.isEmpty()) {
            return Optional.empty();
        }

        long hash = hashFunction.hash(key);
        Long targetHash = ring.ceilingKey(hash);
        if (targetHash == null) {
            targetHash = ring.firstKey();
        }

        T node = (targetHash != null) ? ring.get(targetHash) : null;
        if (node == null && !ring.isEmpty()) {
            // Concurrent modification fallback: retry with firstKey
            try {
                Long first = ring.firstKey();
                node = (first != null) ? ring.get(first) : null;
            } catch (NoSuchElementException ignored) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(node);
    }

    /**
     * Returns the primary node responsible for the key, or throws NoSuchElementException if empty.
     *
     * @param key lookup key
     * @return the target node
     * @throws NoSuchElementException if no nodes are available in the ring
     */
    public T getNodeOrThrow(String key) {
        return getNode(key).orElseThrow(() -> new NoSuchElementException("ConsistentHashRing is empty"));
    }

    /**
     * Returns a replica set of distinct physical nodes for the given key.
     * Virtual nodes belonging to already selected physical nodes are skipped.
     *
     * @param key   the lookup key
     * @param count the requested number of unique replica nodes
     * @return list of distinct physical nodes in clockwise ring order (up to available physical nodes)
     */
    public List<T> getNodes(String key, int count) {
        Objects.requireNonNull(key, "key cannot be null");
        if (count <= 0 || ring.isEmpty()) {
            return List.of();
        }

        int targetCount = Math.min(count, physicalNodes.size());
        Set<T> selected = new LinkedHashSet<>(targetCount);
        long hash = hashFunction.hash(key);

        // Traverse clockwise from ceilingKey to end of ring
        for (Map.Entry<Long, T> entry : ring.tailMap(hash, true).entrySet()) {
            selected.add(entry.getValue());
            if (selected.size() == targetCount) {
                return List.copyOf(selected);
            }
        }

        // Wrap around from start of ring up to ceilingKey
        for (Map.Entry<Long, T> entry : ring.headMap(hash, false).entrySet()) {
            selected.add(entry.getValue());
            if (selected.size() == targetCount) {
                return List.copyOf(selected);
            }
        }

        return List.copyOf(selected);
    }

    /**
     * Checks if the given physical node is registered in the ring.
     */
    public boolean containsNode(T node) {
        return physicalNodes.contains(node);
    }

    /**
     * Returns an unmodifiable set of all registered physical nodes.
     */
    public Set<T> getNodes() {
        return Collections.unmodifiableSet(physicalNodes);
    }

    /**
     * Returns the count of registered physical nodes.
     */
    public int getNodeCount() {
        return physicalNodes.size();
    }

    /**
     * Returns the total count of virtual nodes currently on the ring.
     */
    public int getVirtualNodeCount() {
        return ring.size();
    }

    /**
     * Returns configured virtual nodes per node.
     */
    public int getVirtualNodesPerNode() {
        return virtualNodesPerNode;
    }

    /**
     * Clears all nodes from the ring.
     */
    public void clear() {
        ring.clear();
        physicalNodes.clear();
    }
}
