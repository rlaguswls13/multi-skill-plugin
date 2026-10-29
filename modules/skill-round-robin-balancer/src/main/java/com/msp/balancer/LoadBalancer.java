package com.msp.balancer;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Generic load balancer interface for routing requests across a pool of nodes.
 *
 * @param <T> node identifier or connection target type
 */
public interface LoadBalancer<T> {

    /**
     * Selects the next node according to the load balancing algorithm.
     *
     * @return selected node
     * @throws NoSuchElementException if no active nodes are available
     */
    T next();

    /**
     * Attempts to select the next node, returning an empty Optional if no nodes are available.
     *
     * @return Optional containing the selected node, or empty if pool is empty
     */
    Optional<T> tryNext();

    /**
     * Returns an unmodifiable snapshot of active nodes currently in the pool.
     */
    List<T> getNodes();

    /**
     * Number of active nodes in the pool.
     */
    int size();

    /**
     * True if no nodes are present in the pool.
     */
    default boolean isEmpty() {
        return size() == 0;
    }
}
