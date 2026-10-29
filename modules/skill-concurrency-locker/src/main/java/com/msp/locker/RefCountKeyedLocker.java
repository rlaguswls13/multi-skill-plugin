package com.msp.locker;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Thread-safe reference-counted KeyedLocker.
 * Automatically evicts locks from memory as soon as no thread is holding or waiting on the key.
 */
public final class RefCountKeyedLocker<K> implements KeyedLocker<K> {

    private static final class RefLock {
        final ReentrantLock lock;
        int refCount;

        RefLock(boolean fair) {
            this.lock = new ReentrantLock(fair);
            this.refCount = 1;
        }
    }

    private final boolean fair;
    private final ConcurrentHashMap<K, RefLock> locks = new ConcurrentHashMap<>();

    public RefCountKeyedLocker() {
        this(false);
    }

    public RefCountKeyedLocker(boolean fair) {
        this.fair = fair;
    }

    @Override
    public <T> T withLock(K key, Supplier<T> action) {
        return withLock(key, null, action);
    }

    @Override
    public <T> T withLock(K key, Duration timeout, Supplier<T> action) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        RefLock refLock = locks.compute(key, (k, existing) -> {
            if (existing == null) {
                return new RefLock(fair);
            }
            existing.refCount++;
            return existing;
        });

        boolean acquired = false;
        try {
            if (timeout == null) {
                refLock.lock.lockInterruptibly();
                acquired = true;
            } else {
                acquired = refLock.lock.tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS);
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
                refLock.lock.unlock();
            }
            locks.compute(key, (k, existing) -> {
                if (existing == null) {
                    return null;
                }
                existing.refCount--;
                return existing.refCount <= 0 ? null : existing;
            });
        }
    }

    @Override
    public int activeLockCount() {
        return locks.size();
    }

    @Override
    public boolean isLocked(K key) {
        Objects.requireNonNull(key, "key cannot be null");
        RefLock refLock = locks.get(key);
        return refLock != null && refLock.lock.isLocked();
    }
}
