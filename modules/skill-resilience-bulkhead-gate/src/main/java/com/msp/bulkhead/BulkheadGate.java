package com.msp.bulkhead;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * High-performance, zero-dependency concurrency isolator and Bulkhead Gate.
 * <p>
 * Implements the Bulkhead pattern using a fair {@link Semaphore} and bounded wait queue management.
 * Protects downstream services and critical components from cascading failures by isolating concurrent execution.
 */
public final class BulkheadGate {

    private final BulkheadConfig config;
    private final Semaphore semaphore;
    private final AtomicInteger queueDepth = new AtomicInteger(0);

    /**
     * Creates a bulkhead gate with the specified configuration.
     *
     * @param config the bulkhead configuration
     */
    public BulkheadGate(BulkheadConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.semaphore = new Semaphore(config.maxConcurrentCalls(), true);
    }

    /**
     * Creates a bulkhead gate with the given maximum concurrent calls and no wait queue.
     *
     * @param maxConcurrentCalls maximum concurrent calls permitted
     */
    public BulkheadGate(int maxConcurrentCalls) {
        this(BulkheadConfig.of(maxConcurrentCalls));
    }

    /**
     * Creates a bulkhead gate with default settings (10 concurrent calls, 10 wait queue, 500ms timeout).
     */
    public static BulkheadGate ofDefaults() {
        return new BulkheadGate(BulkheadConfig.ofDefaults());
    }

    /**
     * Attempts to acquire an execution permit immediately without queuing or blocking.
     *
     * @return true if permit was acquired, false otherwise
     */
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    /**
     * Releases an acquired permit back to the bulkhead.
     */
    public void release() {
        semaphore.release();
    }

    /**
     * Acquires permission to execute, waiting in the bounded queue if necessary.
     *
     * @throws BulkheadFullException if the wait queue is full or if the wait timeout expires
     */
    public void acquirePermission() {
        // Fast path: attempt immediate acquisition without entering queue
        if (semaphore.tryAcquire()) {
            return;
        }

        // Check if queue waiting is disabled
        if (config.maxWaitQueueSize() <= 0 || config.maxWaitDuration().isZero()) {
            throw new BulkheadFullException("Bulkhead is full: all " + config.maxConcurrentCalls()
                    + " permits in use and wait queue is disabled");
        }

        // Bounded queue reservation
        while (true) {
            int currentWaiters = queueDepth.get();
            if (currentWaiters >= config.maxWaitQueueSize()) {
                throw new BulkheadFullException("Bulkhead wait queue is full: depth=" + currentWaiters
                        + " >= max=" + config.maxWaitQueueSize());
            }
            if (queueDepth.compareAndSet(currentWaiters, currentWaiters + 1)) {
                break;
            }
        }

        boolean acquired = false;
        try {
            long timeoutNanos = config.maxWaitDuration().toNanos();
            acquired = semaphore.tryAcquire(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BulkheadFullException("Interrupted while waiting for bulkhead permit", e);
        } finally {
            queueDepth.decrementAndGet();
        }

        if (!acquired) {
            throw new BulkheadFullException("Bulkhead wait timeout expired after " + config.maxWaitDuration());
        }
    }

    /**
     * Executes the given supplier within the bulkhead permit boundary.
     *
     * @param supplier the computation to run
     * @param <T>      result type
     * @return result of the supplier
     * @throws BulkheadFullException if permit cannot be acquired
     */
    public <T> T executeSupplier(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        acquirePermission();
        try {
            return supplier.get();
        } finally {
            release();
        }
    }

    /**
     * Executes the given runnable within the bulkhead permit boundary.
     *
     * @param runnable the action to run
     * @throws BulkheadFullException if permit cannot be acquired
     */
    public void executeRunnable(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        acquirePermission();
        try {
            runnable.run();
        } finally {
            release();
        }
    }

    /**
     * Executes the given callable within the bulkhead permit boundary.
     *
     * @param callable the action to run
     * @param <T>      result type
     * @return result of the callable
     * @throws Exception            if callable throws
     * @throws BulkheadFullException if permit cannot be acquired
     */
    public <T> T executeCallable(Callable<T> callable) throws Exception {
        Objects.requireNonNull(callable, "callable must not be null");
        acquirePermission();
        try {
            return callable.call();
        } finally {
            release();
        }
    }

    /**
     * Returns the number of currently available execution permits.
     */
    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Returns the current number of threads waiting in the queue.
     */
    public int getQueueDepth() {
        return queueDepth.get();
    }

    /**
     * Returns the number of currently active concurrent executions.
     */
    public int getCurrentConcurrentCalls() {
        return Math.max(0, config.maxConcurrentCalls() - semaphore.availablePermits());
    }

    /**
     * Captures a real-time snapshot of bulkhead performance metrics.
     */
    public BulkheadMetrics getMetrics() {
        int available = getAvailablePermits();
        int active = Math.max(0, config.maxConcurrentCalls() - available);
        return new BulkheadMetrics(
                available,
                config.maxConcurrentCalls(),
                active,
                getQueueDepth(),
                config.maxWaitQueueSize()
        );
    }

    /**
     * Returns the underlying configuration.
     */
    public BulkheadConfig getConfig() {
        return config;
    }
}
