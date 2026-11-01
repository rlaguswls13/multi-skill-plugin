package com.msp.cuckoo;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe concurrent wrapper for {@link CuckooFilter}.
 *
 * <p>Uses Read/Write locking allowing multiple concurrent queries while ensuring
 * thread-safe insertions and deletions.</p>
 *
 * @param <T> type of elements
 */
public class ConcurrentCuckooFilter<T> {

    private final CuckooFilter<T> delegate;
    private final ReentrantReadWriteLock rwLock;

    public ConcurrentCuckooFilter(CuckooFilter<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.rwLock = new ReentrantReadWriteLock();
    }

    public static ConcurrentCuckooFilter<String> createForStrings(int expectedCapacity) {
        return new ConcurrentCuckooFilter<>(CuckooFilter.createForStrings(expectedCapacity));
    }

    public static <T> ConcurrentCuckooFilter<T> create(Hasher<T> hasher, int expectedCapacity) {
        return new ConcurrentCuckooFilter<>(CuckooFilter.create(hasher, expectedCapacity));
    }

    public static <T> ConcurrentCuckooFilter<T> create(int expectedCapacity) {
        return new ConcurrentCuckooFilter<>(CuckooFilter.create(expectedCapacity));
    }

    public boolean put(T item) {
        rwLock.writeLock().lock();
        try {
            return delegate.put(item);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public boolean contains(T item) {
        rwLock.readLock().lock();
        try {
            return delegate.contains(item);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean remove(T item) {
        rwLock.writeLock().lock();
        try {
            return delegate.remove(item);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public int getCount() {
        rwLock.readLock().lock();
        try {
            return delegate.getCount();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int getCapacity() {
        return delegate.getCapacity();
    }

    public double getLoadFactor() {
        rwLock.readLock().lock();
        try {
            return delegate.getLoadFactor();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean isEmpty() {
        rwLock.readLock().lock();
        try {
            return delegate.isEmpty();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void clear() {
        rwLock.writeLock().lock();
        try {
            delegate.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
