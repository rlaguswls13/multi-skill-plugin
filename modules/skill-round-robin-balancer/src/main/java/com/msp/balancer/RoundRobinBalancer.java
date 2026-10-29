package com.msp.balancer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe atomic cyclic round-robin load balancer.
 *
 * @param <T> node type
 */
public final class RoundRobinBalancer<T> implements LoadBalancer<T> {

    private final CopyOnWriteArrayList<T> nodes = new CopyOnWriteArrayList<>();
    private final AtomicInteger index = new AtomicInteger(0);

    public RoundRobinBalancer() {
    }

    public RoundRobinBalancer(Collection<? extends T> initialNodes) {
        if (initialNodes != null) {
            for (T node : initialNodes) {
                addNode(node);
            }
        }
    }

    /**
     * Adds a node to the pool.
     */
    public RoundRobinBalancer<T> addNode(T node) {
        Objects.requireNonNull(node, "node cannot be null");
        nodes.add(node);
        return this;
    }

    /**
     * Adds multiple nodes to the pool.
     */
    public RoundRobinBalancer<T> addAll(Collection<? extends T> items) {
        if (items != null) {
            for (T item : items) {
                addNode(item);
            }
        }
        return this;
    }

    /**
     * Removes the first occurrence of the specified node from the pool.
     *
     * @return true if node was removed
     */
    public boolean removeNode(T node) {
        return nodes.remove(node);
    }

    @Override
    public T next() {
        int currentSize = nodes.size();
        if (currentSize == 0) {
            throw new NoSuchElementException("No available nodes in RoundRobinBalancer");
        }
        int pos = Math.floorMod(index.getAndIncrement(), currentSize);
        try {
            return nodes.get(pos);
        } catch (IndexOutOfBoundsException e) {
            // Concurrent removal occurred, retry once
            currentSize = nodes.size();
            if (currentSize == 0) {
                throw new NoSuchElementException("No available nodes in RoundRobinBalancer");
            }
            return nodes.get(Math.floorMod(index.getAndIncrement(), currentSize));
        }
    }

    @Override
    public Optional<T> tryNext() {
        if (nodes.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(next());
        } catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<T> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    @Override
    public int size() {
        return nodes.size();
    }
}
