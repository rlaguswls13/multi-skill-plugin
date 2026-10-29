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
 * Thread-safe Classic Weighted Round-Robin load balancer (LVS / IPVS algorithm) using GCD.
 *
 * @param <T> node type
 */
public final class WeightedRoundRobinBalancer<T> implements LoadBalancer<T> {

    public record WeightedNode<T>(T node, int weight) {
        public WeightedNode {
            Objects.requireNonNull(node, "node cannot be null");
            if (weight <= 0) {
                throw new IllegalArgumentException("Weight must be strictly positive (> 0), got: " + weight);
            }
        }
    }

    private final List<WeightedNode<T>> nodes = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    private int currentIndex = -1;
    private int currentWeight = 0;
    private int maxWeight = 0;
    private int gcdWeight = 1;

    public WeightedRoundRobinBalancer() {
    }

    public WeightedRoundRobinBalancer(Map<T, Integer> nodeWeights) {
        if (nodeWeights != null) {
            for (Map.Entry<T, Integer> entry : nodeWeights.entrySet()) {
                addNode(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Adds a node with weight 1.
     */
    public WeightedRoundRobinBalancer<T> addNode(T node) {
        return addNode(node, 1);
    }

    /**
     * Adds a node with specified positive weight.
     */
    public WeightedRoundRobinBalancer<T> addNode(T node, int weight) {
        lock.lock();
        try {
            nodes.add(new WeightedNode<>(node, weight));
            recomputeParameters();
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
            boolean removed = nodes.removeIf(wn -> wn.node().equals(node));
            if (removed) {
                recomputeParameters();
            }
            return removed;
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
                throw new NoSuchElementException("No available nodes in WeightedRoundRobinBalancer");
            }
            if (n == 1) {
                return nodes.get(0).node();
            }

            while (true) {
                currentIndex = (currentIndex + 1) % n;
                if (currentIndex == 0) {
                    currentWeight -= gcdWeight;
                    if (currentWeight <= 0) {
                        currentWeight = maxWeight;
                        if (currentWeight == 0) {
                            throw new NoSuchElementException("Total weight of nodes is zero");
                        }
                    }
                }
                WeightedNode<T> current = nodes.get(currentIndex);
                if (current.weight() >= currentWeight) {
                    return current.node();
                }
            }
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
            for (WeightedNode<T> wn : nodes) {
                list.add(wn.node());
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

    public int getWeight(T node) {
        lock.lock();
        try {
            for (WeightedNode<T> wn : nodes) {
                if (wn.node().equals(node)) {
                    return wn.weight();
                }
            }
            throw new NoSuchElementException("Node not found: " + node);
        } finally {
            lock.unlock();
        }
    }

    private void recomputeParameters() {
        currentIndex = -1;
        currentWeight = 0;
        if (nodes.isEmpty()) {
            maxWeight = 0;
            gcdWeight = 1;
            return;
        }

        int max = 0;
        int gcd = nodes.get(0).weight();
        for (WeightedNode<T> wn : nodes) {
            if (wn.weight() > max) {
                max = wn.weight();
            }
            gcd = gcd(gcd, wn.weight());
        }
        this.maxWeight = max;
        this.gcdWeight = gcd;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }
}
