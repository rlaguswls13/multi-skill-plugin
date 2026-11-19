package com.msp.stripedlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Fixed-stripe {@link ReadWriteLock} manager that maps arbitrary keys to a fixed,
 * power-of-2 array of {@link ReentrantReadWriteLock} instances using avalanche hashing.
 *
 * <p>This bounds lock memory to O(stripes) regardless of key cardinality while
 * minimizing contention across concurrent threads. Multi-key bulk operations
 * deduplicate and sort stripe indices in strictly ascending order before acquisition,
 * eliminating lock-ordering deadlocks.
 *
 * @param <K> key type
 */
public final class StripedReadWriteLock<K> {

    /** Maximum allowed stripe count (1,048,576) to guard against accidental OOM. */
    public static final int MAX_STRIPES = 1 << 20;

    private final ReentrantReadWriteLock[] stripes;
    private final int mask;
    private final boolean fair;

    /**
     * Creates a non-fair striped lock with at least {@code minStripes} stripes
     * (rounded up to the next power of 2).
     */
    public static <K> StripedReadWriteLock<K> create(int minStripes) {
        return new StripedReadWriteLock<>(minStripes, false);
    }

    /**
     * Creates a striped lock with at least {@code minStripes} stripes
     * (rounded up to the next power of 2) and configurable fairness.
     */
    public static <K> StripedReadWriteLock<K> create(int minStripes, boolean fair) {
        return new StripedReadWriteLock<>(minStripes, fair);
    }

    /**
     * Constructs a striped lock with the requested minimum stripe count and fairness policy.
     *
     * @param minStripes minimum stripe count (must be between 1 and {@link #MAX_STRIPES})
     * @param fair       {@code true} to use fair {@link ReentrantReadWriteLock} stripes
     */
    public StripedReadWriteLock(int minStripes, boolean fair) {
        if (minStripes < 1 || minStripes > MAX_STRIPES) {
            throw new IllegalArgumentException(
                    "minStripes must be between 1 and " + MAX_STRIPES + ", got: " + minStripes);
        }
        int stripeCount = nextPowerOfTwo(minStripes);
        this.stripes = new ReentrantReadWriteLock[stripeCount];
        for (int i = 0; i < stripeCount; i++) {
            this.stripes[i] = new ReentrantReadWriteLock(fair);
        }
        this.mask = stripeCount - 1;
        this.fair = fair;
    }

    /**
     * Returns the actual power-of-2 number of stripes.
     */
    public int stripeCount() {
        return stripes.length;
    }

    /**
     * Returns whether the underlying locks use fair ordering.
     */
    public boolean isFair() {
        return fair;
    }

    /**
     * Computes the deterministic stripe index in {@code [0, stripeCount())} for {@code key}.
     */
    public int stripeIndex(K key) {
        Objects.requireNonNull(key, "key must not be null");
        return avalancheHash(key.hashCode()) & mask;
    }

    /**
     * Returns the {@link ReadWriteLock} stripe assigned to {@code key}.
     */
    public ReadWriteLock readWriteLock(K key) {
        return stripes[stripeIndex(key)];
    }

    /**
     * Returns the read {@link Lock} for {@code key}.
     */
    public Lock readLock(K key) {
        return readWriteLock(key).readLock();
    }

    /**
     * Returns the write {@link Lock} for {@code key}.
     */
    public Lock writeLock(K key) {
        return readWriteLock(key).writeLock();
    }

    /**
     * Executes {@code action} while holding the read lock for {@code key}.
     */
    public <T> T withReadLock(K key, Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        Lock lock = readLock(key);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes {@code action} while holding the read lock for {@code key}.
     */
    public void withReadLock(K key, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        Lock lock = readLock(key);
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes {@code action} while holding the write lock for {@code key}.
     */
    public <T> T withWriteLock(K key, Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        Lock lock = writeLock(key);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes {@code action} while holding the write lock for {@code key}.
     */
    public void withWriteLock(K key, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        Lock lock = writeLock(key);
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Acquires write locks for all distinct stripes mapped by {@code keys} in strictly
     * ascending stripe index order to prevent deadlocks.
     *
     * @param keys collection of keys to lock (may contain duplicates or be empty)
     * @return an {@link AutoCloseable} {@link BulkLockHandle}
     */
    public BulkLockHandle bulkWriteLock(Collection<? extends K> keys) {
        return acquireBulk(keys, true);
    }

    /**
     * Acquires read locks for all distinct stripes mapped by {@code keys} in strictly
     * ascending stripe index order.
     *
     * @param keys collection of keys to lock (may contain duplicates or be empty)
     * @return an {@link AutoCloseable} {@link BulkLockHandle}
     */
    public BulkLockHandle bulkReadLock(Collection<? extends K> keys) {
        return acquireBulk(keys, false);
    }

    /**
     * Executes {@code action} while holding write locks for all keys in deadlock-free sorted order.
     */
    public <T> T withBulkWriteLock(Collection<? extends K> keys, Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        try (BulkLockHandle ignored = bulkWriteLock(keys)) {
            return action.get();
        }
    }

    /**
     * Executes {@code action} while holding write locks for all keys in deadlock-free sorted order.
     */
    public void withBulkWriteLock(Collection<? extends K> keys, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        try (BulkLockHandle ignored = bulkWriteLock(keys)) {
            action.run();
        }
    }

    /**
     * Executes {@code action} while holding read locks for all keys in deadlock-free sorted order.
     */
    public <T> T withBulkReadLock(Collection<? extends K> keys, Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        try (BulkLockHandle ignored = bulkReadLock(keys)) {
            return action.get();
        }
    }

    /**
     * Executes {@code action} while holding read locks for all keys in deadlock-free sorted order.
     */
    public void withBulkReadLock(Collection<? extends K> keys, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        try (BulkLockHandle ignored = bulkReadLock(keys)) {
            action.run();
        }
    }

    private BulkLockHandle acquireBulk(Collection<? extends K> keys, boolean write) {
        Objects.requireNonNull(keys, "keys must not be null");
        if (keys.isEmpty()) {
            return new BulkLockHandle(List.of());
        }
        int[] indices = new int[keys.size()];
        int pos = 0;
        for (K key : keys) {
            indices[pos++] = stripeIndex(key);
        }
        Arrays.sort(indices);

        List<Lock> acquired = new ArrayList<>(indices.length);
        try {
            int prev = -1;
            for (int idx : indices) {
                if (idx == prev) {
                    continue; // Deduplicate identical stripes
                }
                prev = idx;
                Lock lock = write ? stripes[idx].writeLock() : stripes[idx].readLock();
                lock.lock();
                acquired.add(lock);
            }
            return new BulkLockHandle(acquired);
        } catch (Throwable t) {
            for (int i = acquired.size() - 1; i >= 0; i--) {
                acquired.get(i).unlock();
            }
            throw t;
        }
    }

    /**
     * Murmur3 32-bit finalizer avalanche hash to eliminate low-bit collisions.
     */
    static int avalancheHash(int h) {
        h ^= (h >>> 16);
        h *= 0x85ebca6b;
        h ^= (h >>> 13);
        h *= 0xc2b2ae35;
        h ^= (h >>> 16);
        return h;
    }

    private static int nextPowerOfTwo(int n) {
        if ((n & (n - 1)) == 0) {
            return n;
        }
        return Integer.highestOneBit(n) << 1;
    }
}
