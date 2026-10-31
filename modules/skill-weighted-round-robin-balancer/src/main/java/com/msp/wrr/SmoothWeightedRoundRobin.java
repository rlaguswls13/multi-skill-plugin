package com.msp.wrr;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Nginx and LVS standard Smooth Weighted Round-Robin (SWRR) load balancer.
 * <p>
 * Standard Weighted Round-Robin suffers from burstiness (e.g. servers with weights 5, 1, 1 produce
 * sequences like A, A, A, A, A, B, C, overloading server A during the initial burst).
 * <p>
 * Smooth Weighted Round-Robin interleaves requests evenly (e.g. A, A, B, A, C, A, A), guaranteeing
 * that each server is chosen proportionally to its weight over {@code sum(weights)} iterations
 * while dispersing the load evenly across all servers.
 * <p>
 * This class is fully thread-safe.
 *
 * @param <T> server or target descriptor type
 */
public final class SmoothWeightedRoundRobin<T> {

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<T, ServerNode<T>> serverMap = new LinkedHashMap<>();
    private final List<ServerNode<T>> serverList = new ArrayList<>();
    private int totalWeight = 0;

    public SmoothWeightedRoundRobin() {
    }

    /**
     * Constructs a balancer initialized with the given server weights.
     *
     * @param initialServers map of server to positive integer weight
     */
    public SmoothWeightedRoundRobin(Map<T, Integer> initialServers) {
        if (initialServers != null) {
            for (Map.Entry<T, Integer> entry : initialServers.entrySet()) {
                addServer(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Adds or updates a server with the specified positive weight.
     *
     * @param server server instance (cannot be null)
     * @param weight positive weight (must be > 0)
     */
    public void addServer(T server, int weight) {
        if (server == null) {
            throw new IllegalArgumentException("Server cannot be null");
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be greater than 0, got: " + weight);
        }

        lock.lock();
        try {
            ServerNode<T> existing = serverMap.get(server);
            if (existing != null) {
                totalWeight += (weight - existing.weight);
                existing.weight = weight;
            } else {
                ServerNode<T> node = new ServerNode<>(server, weight);
                serverMap.put(server, node);
                serverList.add(node);
                totalWeight += weight;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a server from the balancer pool.
     *
     * @param server server to remove
     * @return {@code true} if the server was present and removed, {@code false} otherwise
     */
    public boolean removeServer(T server) {
        if (server == null) {
            return false;
        }

        lock.lock();
        try {
            ServerNode<T> removed = serverMap.remove(server);
            if (removed != null) {
                serverList.remove(removed);
                totalWeight -= removed.weight;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Selects the next server according to Nginx Smooth Weighted Round-Robin algorithm.
     *
     * @return selected server, or {@code null} if no servers are registered
     */
    public T select() {
        lock.lock();
        try {
            if (serverList.isEmpty() || totalWeight <= 0) {
                return null;
            }

            ServerNode<T> best = null;
            for (ServerNode<T> node : serverList) {
                node.currentWeight += node.weight;
                if (best == null || node.currentWeight > best.currentWeight) {
                    best = node;
                }
            }

            if (best != null) {
                best.currentWeight -= totalWeight;
                return best.server;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Selects a sequence of {@code count} servers in order.
     *
     * @param count number of selections to make
     * @return list of selected servers (empty list if count <= 0 or balancer is empty)
     */
    public List<T> selectSequence(int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        lock.lock();
        try {
            if (serverList.isEmpty() || totalWeight <= 0) {
                return Collections.emptyList();
            }

            List<T> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                T chosen = select();
                if (chosen != null) {
                    result.add(chosen);
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the current dynamic weights of all servers back to zero.
     */
    public void reset() {
        lock.lock();
        try {
            for (ServerNode<T> node : serverList) {
                node.currentWeight = 0;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes all servers and resets total weight.
     */
    public void clear() {
        lock.lock();
        try {
            serverMap.clear();
            serverList.clear();
            totalWeight = 0;
        } finally {
            lock.unlock();
        }
    }

    public int getServerCount() {
        lock.lock();
        try {
            return serverList.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        return getServerCount() == 0;
    }

    public int getTotalWeight() {
        lock.lock();
        try {
            return totalWeight;
        } finally {
            lock.unlock();
        }
    }

    public Map<T, Integer> getServerWeights() {
        lock.lock();
        try {
            Map<T, Integer> copy = new LinkedHashMap<>();
            for (ServerNode<T> node : serverList) {
                copy.put(node.server, node.weight);
            }
            return Collections.unmodifiableMap(copy);
        } finally {
            lock.unlock();
        }
    }

    private static final class ServerNode<T> {
        final T server;
        int weight;
        int currentWeight;

        ServerNode(T server, int weight) {
            this.server = server;
            this.weight = weight;
            this.currentWeight = 0;
        }
    }
}
