package com.msp.balancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe Smooth Weighted Round-Robin load balancer (Nginx upstream algorithm).
 * <p>
 * Eliminates burst clustering of high-weight nodes by smoothly interleaving requests
 * across all nodes (e.g. weights 5:1:1 produces sequence [A, A, B, A, C, A, A] instead
 * of clustering 5 consecutive requests to A).
 *
 * @param <T> node type
 */
public final class SmoothWeightedRoundRobinBalancer<T> implements LoadBalancer<T> {

    private static final class NodeHolder<T> {
        final T node;
        final int weight;
        int currentWeight;

        NodeHolder(T node, int weight) {
            this.node = Objects.requireNonNull(node, "node cannot be null");
            if (weight <= 0) {
                throw new IllegalArgumentException("Weight must be strictly positive (> 0), got: " + weight);
            }
            this.weight = weight;
            this.currentWeight = 0;
        }
    }

    private final List<NodeHolder<T>> nodes = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private int totalWeight = 0;

    public SmoothWeightedRoundRobinBalancer() {
    }

    public SmoothWeightedRoundRobinBalancer(Map<T, Integer> nodeWeights) {
        if (nodeWeights != null) {
            for (Map.Entry<T, Integer> entry : nodeWeights.entrySet()) {
                addNode(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Adds a node with weight 1.
     */
    public SmoothWeightedRoundRobinBalancer<T> addNode(T node) {
        return addNode(node, 1);
    }

    /**
     * Adds a node with specified positive weight.
     */
    public SmoothWeightedRoundRobinBalancer<T> addNode(T node, int weight) {
        lock.lock();
        try {
            nodes.add(new NodeHolder<>(node, weight));
            totalWeight += weight;
            return this;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes the node from the balancer pool.
     */
    public boolean removeNode(T node) {
        lock.lock();
        try {
            NodeHolder<T> found = null;
            for (NodeHolder<T> holder : nodes) {
                if (holder.node.equals(node)) {
                    found = holder;
                    break;
                }
            }
            if (found != null) {
                nodes.remove(found);
                totalWeight -= found.weight;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T next() {
        lock.lock();
        try {
            int n = nodes.size();
            if (n == 0) {
                throw new NoSuchElementException("No available nodes in SmoothWeightedRoundRobinBalancer");
            }
            if (n == 1) {
                return nodes.get(0).node;
            }

            NodeHolder<T> best = null;
            int maxCurrent = Integer.MIN_VALUE;

            for (NodeHolder<T> holder : nodes) {
                // Step 1: Add configured weight to current dynamic weight
                holder.currentWeight += holder.weight;

                // Step 2: Track node with maximum current dynamic weight
                if (best == null || holder.currentWeight > maxCurrent) {
                    maxCurrent = holder.currentWeight;
                    best = holder;
                }
            }

            // Step 3: Subtract totalWeight from chosen best node
            best.currentWeight -= totalWeight;

            return best.node;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<T> tryNext() {
        lock.lock();
        try {
            if (nodes.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(next());
        } catch (NoSuchElementException e) {
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<T> getNodes() {
        lock.lock();
        try {
            List<T> list = new ArrayList<>(nodes.size());
            for (NodeHolder<T> holder : nodes) {
                list.add(holder.node);
            }
            return Collections.unmodifiableList(list);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return nodes.size();
        } finally {
            lock.unlock();
        }
    }

    public int getTotalWeight() {
        lock.lock();
        try {
            return totalWeight;
        } finally {
            lock.unlock();
        }
    }

    public int getWeight(T node) {
        lock.lock();
        try {
            for (NodeHolder<T> holder : nodes) {
                if (holder.node.equals(node)) {
                    return holder.weight;
                }
            }
            throw new NoSuchElementException("Node not found: " + node);
        } finally {
            lock.unlock();
        }
    }
}
