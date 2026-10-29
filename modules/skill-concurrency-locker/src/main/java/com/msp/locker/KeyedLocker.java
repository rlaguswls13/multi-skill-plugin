package com.msp.locker;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Key-based lock manager for synchronizing operations on specific granular keys (e.g. userId, accountId).
 *
 * @param <K> type of the key
 */
public interface KeyedLocker<K> {

    /**
     * Executes the supplier with the lock held on the given key.
     *
     * @param key    the key to lock on
     * @param action the supplier to execute
     * @param <T>    the return type
     * @return the result of the supplier
     */
    <T> T withLock(K key, Supplier<T> action);

    /**
     * Executes the supplier with the lock held on the given key, waiting up to the given timeout.
     *
     * @param key     the key to lock on
     * @param timeout maximum time to wait for the lock
     * @param action  the supplier to execute
     * @param <T>     the return type
     * @return the result of the supplier
     * @throws LockTimeoutException if lock acquisition times out
     */
    <T> T withLock(K key, Duration timeout, Supplier<T> action);

    /**
     * Executes the runnable with the lock held on the given key.
     *
     * @param key    the key to lock on
     * @param action the runnable to execute
     */
    default void withLock(K key, Runnable action) {
        Objects.requireNonNull(action, "action cannot be null");
        withLock(key, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Executes the runnable with the lock held on the given key, waiting up to the given timeout.
     *
     * @param key     the key to lock on
     * @param timeout maximum time to wait for the lock
     * @param action  the runnable to execute
     * @throws LockTimeoutException if lock acquisition times out
     */
    default void withLock(K key, Duration timeout, Runnable action) {
        Objects.requireNonNull(action, "action cannot be null");
        withLock(key, timeout, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Returns the number of currently active locks tracked in memory.
     */
    int activeLockCount();

    /**
     * Checks if the given key is currently locked.
     */
    boolean isLocked(K key);
}
