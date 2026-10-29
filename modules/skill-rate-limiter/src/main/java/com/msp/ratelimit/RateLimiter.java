package com.msp.ratelimit;

import java.time.Duration;

/**
 * Common rate limiter interface for controlling execution throughput.
 */
public interface RateLimiter {

    /**
     * Attempts to acquire 1 permit without blocking.
     *
     * @return {@code true} if permit was acquired, {@code false} otherwise
     */
    default boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * Attempts to acquire the requested number of permits without blocking.
     *
     * @param permits number of permits to acquire (must be > 0)
     * @return {@code true} if permits were acquired, {@code false} otherwise
     */
    boolean tryAcquire(int permits);

    /**
     * Attempts to acquire 1 permit waiting up to the given timeout.
     *
     * @param timeout maximum duration to wait
     * @return {@code true} if acquired, {@code false} if timeout elapsed
     * @throws InterruptedException if current thread is interrupted while waiting
     */
    default boolean tryAcquire(Duration timeout) throws InterruptedException {
        return tryAcquire(1, timeout);
    }

    /**
     * Attempts to acquire the requested permits waiting up to the given timeout.
     *
     * @param permits number of permits to acquire (must be > 0)
     * @param timeout maximum duration to wait
     * @return {@code true} if acquired, {@code false} if timeout elapsed
     * @throws InterruptedException if current thread is interrupted while waiting
     */
    boolean tryAcquire(int permits, Duration timeout) throws InterruptedException;

    /**
     * Acquires 1 permit, blocking until available.
     *
     * @throws InterruptedException if current thread is interrupted while waiting
     */
    default void acquire() throws InterruptedException {
        acquire(1);
    }

    /**
     * Acquires requested permits, blocking until available.
     *
     * @param permits number of permits to acquire
     * @throws InterruptedException if current thread is interrupted while waiting
     */
    default void acquire(int permits) throws InterruptedException {
        while (!tryAcquire(permits, Duration.ofDays(365))) {
            // Loop until acquired
        }
    }

    /**
     * Returns the approximate number of currently available permits.
     *
     * @return available permits
     */
    long availablePermits();
}
