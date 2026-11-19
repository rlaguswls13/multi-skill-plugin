package com.msp.stripedlock;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

/**
 * AutoCloseable handle representing one or more acquired stripe locks.
 *
 * <p>Calling {@link #close()} or {@link #unlock()} releases all acquired locks in reverse
 * acquisition order (LIFO) and is idempotent.
 */
public final class BulkLockHandle implements AutoCloseable {

    private final List<Lock> acquiredLocks;
    private final AtomicBoolean released = new AtomicBoolean(false);

    BulkLockHandle(List<Lock> acquiredLocks) {
        this.acquiredLocks = acquiredLocks;
    }

    /**
     * Returns the number of distinct stripe locks held by this handle.
     */
    public int lockedStripeCount() {
        return acquiredLocks.size();
    }

    /**
     * Returns {@code true} if the locks are still held and have not yet been released.
     */
    public boolean isLocked() {
        return !released.get();
    }

    /**
     * Releases all acquired locks in reverse acquisition order. Idempotent.
     */
    public void unlock() {
        if (released.compareAndSet(false, true)) {
            for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
                acquiredLocks.get(i).unlock();
            }
        }
    }

    @Override
    public void close() {
        unlock();
    }
}
