package com.msp.concurrencygate;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * High-performance, non-blocking concurrency regulator designed for asynchronous workflows
 * (Virtual Threads, CompletableFuture, HTTP clients, and database connection pools).
 * <p>
 * Controls the maximum number of concurrent active executions while queuing incoming tasks
 * up to a bounded capacity with per-task queue timeout guarantees.
 */
public final class ConcurrencyGate implements AutoCloseable {

    private final int maxConcurrency;
    private final int maxQueueCapacity;
    private final Duration defaultQueueTimeout;

    private final Object lock = new Object();
    private int activeCount = 0;
    private final Queue<CompletableFuture<Void>> waitQueue = new ArrayDeque<>();

    private final AtomicLong totalAcquired = new AtomicLong(0);
    private final AtomicLong totalRejected = new AtomicLong(0);

    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;

    public ConcurrencyGate(int maxConcurrency, int maxQueueCapacity, Duration defaultQueueTimeout) {
        this(maxConcurrency, maxQueueCapacity, defaultQueueTimeout, null);
    }

    public ConcurrencyGate(int maxConcurrency, int maxQueueCapacity, Duration defaultQueueTimeout,
                           ScheduledExecutorService scheduler) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be at least 1");
        }
        if (maxQueueCapacity < 0) {
            throw new IllegalArgumentException("maxQueueCapacity must be non-negative");
        }
        this.maxConcurrency = maxConcurrency;
        this.maxQueueCapacity = maxQueueCapacity;
        this.defaultQueueTimeout = defaultQueueTimeout != null ? defaultQueueTimeout : Duration.ofSeconds(30);

        if (scheduler == null) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "concurrency-gate-timeout-scheduler");
                t.setDaemon(true);
                return t;
            });
            this.ownsScheduler = true;
        } else {
            this.scheduler = scheduler;
            this.ownsScheduler = false;
        }
    }

    public static ConcurrencyGate of(int maxConcurrency) {
        return new ConcurrencyGate(maxConcurrency, Integer.MAX_VALUE, Duration.ofSeconds(60));
    }

    public static ConcurrencyGate of(int maxConcurrency, int maxQueueCapacity) {
        return new ConcurrencyGate(maxConcurrency, maxQueueCapacity, Duration.ofSeconds(30));
    }

    public static ConcurrencyGate of(int maxConcurrency, int maxQueueCapacity, Duration defaultTimeout) {
        return new ConcurrencyGate(maxConcurrency, maxQueueCapacity, defaultTimeout);
    }

    /**
     * Attempts to acquire an execution slot immediately without waiting in the queue.
     *
     * @return {@code true} if acquired, {@code false} otherwise
     */
    public boolean tryAcquire() {
        synchronized (lock) {
            if (activeCount < maxConcurrency) {
                activeCount++;
                totalAcquired.incrementAndGet();
                return true;
            }
            return false;
        }
    }

    /**
     * Attempts to acquire an execution slot, waiting up to the specified timeout in the queue.
     *
     * @param timeout maximum time to wait
     * @return {@code true} if acquired, {@code false} if timed out or capacity exceeded
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean tryAcquire(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout cannot be null");
        CompletableFuture<Void> waiter;

        synchronized (lock) {
            if (activeCount < maxConcurrency) {
                activeCount++;
                totalAcquired.incrementAndGet();
                return true;
            }
            if (waitQueue.size() >= maxQueueCapacity) {
                totalRejected.incrementAndGet();
                return false;
            }
            waiter = new CompletableFuture<>();
            waitQueue.add(waiter);
        }

        long millis = Math.max(1, timeout.toMillis());
        try {
            waiter.get(millis, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException | InterruptedException e) {
            boolean removed;
            synchronized (lock) {
                removed = waitQueue.remove(waiter);
            }
            if (!removed && waiter.isDone() && !waiter.isCompletedExceptionally()) {
                // Permit was already granted just before timeout/interrupt!
                release();
            } else {
                totalRejected.incrementAndGet();
            }

            if (e instanceof InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            }
            return false;
        } catch (ExecutionException e) {
            return false;
        }
    }

    /**
     * Releases an active execution slot. If any queued tasks are waiting, the slot is immediately
     * transferred to the next waiting task in FIFO order.
     */
    public void release() {
        CompletableFuture<Void> nextWaiter = null;
        synchronized (lock) {
            while (!waitQueue.isEmpty()) {
                CompletableFuture<Void> candidate = waitQueue.poll();
                if (!candidate.isDone()) {
                    nextWaiter = candidate;
                    break;
                }
            }

            if (nextWaiter != null) {
                // Slot is directly handed over to the waiter; activeCount remains constant
                totalAcquired.incrementAndGet();
            } else {
                if (activeCount > 0) {
                    activeCount--;
                }
            }
        }

        if (nextWaiter != null) {
            nextWaiter.complete(null);
        }
    }

    /**
     * Executes an asynchronous task returning a {@link CompletableFuture}, subject to the gate's concurrency limits.
     *
     * @param taskSupplier supplier that initiates the asynchronous operation
     * @param <T>          return type
     * @return CompletableFuture representing the asynchronous result
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<CompletableFuture<T>> taskSupplier) {
        return supplyAsync(taskSupplier, defaultQueueTimeout);
    }

    /**
     * Executes an asynchronous task with a custom queue timeout.
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<CompletableFuture<T>> taskSupplier, Duration queueTimeout) {
        Objects.requireNonNull(taskSupplier, "taskSupplier cannot be null");
        Objects.requireNonNull(queueTimeout, "queueTimeout cannot be null");

        CompletableFuture<Void> permitFuture = acquireAsync(queueTimeout);

        return permitFuture.thenCompose(ignored -> {
            try {
                CompletableFuture<T> taskFuture = taskSupplier.get();
                if (taskFuture == null) {
                    release();
                    return CompletableFuture.failedFuture(new NullPointerException("taskSupplier returned null CompletableFuture"));
                }
                return taskFuture.whenComplete((res, ex) -> release());
            } catch (Throwable t) {
                release();
                return CompletableFuture.failedFuture(t);
            }
        });
    }

    /**
     * Submits a synchronous {@link Callable} task to be executed on the Common ForkJoinPool once a slot is acquired.
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return submit(task, defaultQueueTimeout);
    }

    /**
     * Submits a synchronous {@link Callable} task with a custom queue wait timeout.
     */
    public <T> CompletableFuture<T> submit(Callable<T> task, Duration queueTimeout) {
        Objects.requireNonNull(task, "task cannot be null");
        return supplyAsync(() -> CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new CompletionException(e);
            }
        }), queueTimeout);
    }

    /**
     * Executes a {@link Runnable} task asynchronously once a permit is acquired.
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        return runAsync(task, defaultQueueTimeout);
    }

    /**
     * Executes a {@link Runnable} task asynchronously with a custom queue timeout.
     */
    public CompletableFuture<Void> runAsync(Runnable task, Duration queueTimeout) {
        Objects.requireNonNull(task, "task cannot be null");
        return supplyAsync(() -> CompletableFuture.runAsync(task), queueTimeout);
    }

    private CompletableFuture<Void> acquireAsync(Duration queueTimeout) {
        CompletableFuture<Void> waiter;

        synchronized (lock) {
            if (activeCount < maxConcurrency) {
                activeCount++;
                totalAcquired.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            }

            if (waitQueue.size() >= maxQueueCapacity) {
                totalRejected.incrementAndGet();
                return CompletableFuture.failedFuture(
                        new GateCapacityExceededException("Gate queue capacity (" + maxQueueCapacity + ") exceeded")
                );
            }

            waiter = new CompletableFuture<>();
            waitQueue.add(waiter);
        }

        // Schedule queue timeout
        long timeoutMs = Math.max(1, queueTimeout.toMillis());
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            boolean removed;
            synchronized (lock) {
                removed = waitQueue.remove(waiter);
            }
            if (removed) {
                totalRejected.incrementAndGet();
                waiter.completeExceptionally(
                        new GateTimeoutException("Timed out after " + timeoutMs + "ms waiting in gate queue")
                );
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        waiter.whenComplete((res, ex) -> {
            timeoutTask.cancel(false);
        });

        return waiter;
    }

    // Observability metrics

    public int getActiveCount() {
        synchronized (lock) {
            return activeCount;
        }
    }

    public int getQueueDepth() {
        synchronized (lock) {
            return waitQueue.size();
        }
    }

    public long getTotalAcquired() {
        return totalAcquired.get();
    }

    public long getTotalRejected() {
        return totalRejected.get();
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public int getMaxQueueCapacity() {
        return maxQueueCapacity;
    }

    public GateMetrics getMetrics() {
        synchronized (lock) {
            return new GateMetrics(
                    activeCount,
                    waitQueue.size(),
                    totalAcquired.get(),
                    totalRejected.get(),
                    maxConcurrency,
                    maxQueueCapacity
            );
        }
    }

    @Override
    public void close() {
        if (ownsScheduler && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}
