package com.msp.locker;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Thread-safe striped KeyedLocker with a fixed number of lock stripes.
 * Guarantees strictly bounded constant memory.
 */
public final class StripedKeyedLocker<K> implements KeyedLocker<K> {

    private final ReentrantLock[] stripes;
    private final int mask;

    public StripedKeyedLocker(int stripeCount) {
        this(stripeCount, false);
    }

    public StripedKeyedLocker(int stripeCount, boolean fair) {
        if (stripeCount < 1) {
            throw new IllegalArgumentException("stripeCount must be >= 1: " + stripeCount);
        }
        // Round up to power of 2 for fast bitwise masking
        int capacity = 1;
        while (capacity < stripeCount) {
            capacity <<= 1;
        }
        this.stripes = new ReentrantLock[capacity];
        for (int i = 0; i < capacity; i++) {
            this.stripes[i] = new ReentrantLock(fair);
        }
        this.mask = capacity - 1;
    }

    private ReentrantLock getLock(K key) {
        int hash = key.hashCode();
        // Spread higher bits down
        hash ^= (hash >>> 16);
        return stripes[hash & mask];
    }

    @Override
    public <T> T withLock(K key, Supplier<T> action) {
        return withLock(key, null, action);
    }

    @Override
    public <T> T withLock(K key, Duration timeout, Supplier<T> action) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        ReentrantLock lock = getLock(key);
        boolean acquired = false;
        try {
            if (timeout == null) {
                lock.lockInterruptibly();
                acquired = true;
            } else {
                acquired = lock.tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS);
                if (!acquired) {
                    throw new LockTimeoutException(key, timeout);
                }
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for key lock: " + key, e);
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }

    @Override
    public int activeLockCount() {
        int count = 0;
        for (ReentrantLock lock : stripes) {
            if (lock.isLocked()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean isLocked(K key) {
        Objects.requireNonNull(key, "key cannot be null");
        return getLock(key).isLocked();
    }
}
