package com.msp.vthread;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Java 21 Virtual Thread runner with concurrency rate-limiting semaphore.
 * <p>
 * While Java 21 Virtual Threads allow spawning millions of concurrent tasks,
 * downstream physical bottlenecks (such as database connection pools, limited HTTP sockets,
 * or rate-limited external APIs) can easily suffer stampedes and collapse under unbounded concurrency.
 * This runner transparently pairs virtual threads with a carrier-friendly semaphore so virtual
 * threads unmount cleanly while waiting for permits without exhausting OS resources.
 * </p>
 */
public class VirtualThreadRunner implements AutoCloseable {

    private final Semaphore semaphore;
    private final int maxConcurrency;
    private final String namePrefix;
    private final AtomicLong threadIdCounter = new AtomicLong(0);

    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final AtomicInteger queuedCount = new AtomicInteger(0);
    private final LongAdder completedCount = new LongAdder();
    private final AtomicLong inFlightCount = new AtomicLong(0);

    private final ReentrantLock finishLock = new ReentrantLock();
    private final Condition allFinished = finishLock.newCondition();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private VirtualThreadRunner(Builder builder) {
        this.maxConcurrency = builder.maxConcurrency;
        this.semaphore = (builder.maxConcurrency > 0)
                ? new Semaphore(builder.maxConcurrency, builder.fair)
                : null;
        this.namePrefix = builder.namePrefix;
    }

    /**
     * Creates a runner with max concurrent permits (e.g. HikariCP pool size or API socket limit).
     *
     * @param permits maximum concurrent executing virtual threads
     * @return new {@link VirtualThreadRunner}
     */
    public static VirtualThreadRunner withMaxConcurrency(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive: " + permits);
        }
        return new Builder().maxConcurrency(permits).build();
    }

    /**
     * Creates an unbounded virtual thread runner without concurrency throttling.
     *
     * @return new {@link VirtualThreadRunner}
     */
    public static VirtualThreadRunner unbounded() {
        return new Builder().maxConcurrency(0).build();
    }

    /**
     * Builder for custom configurations.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Submits a value-returning task to run on a rate-limited virtual thread.
     *
     * @param task callable task
     * @param <T>  result type
     * @return {@link CompletableFuture} tracking task completion
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        checkNotClosed();

        CompletableFuture<T> future = new CompletableFuture<>();
        inFlightCount.incrementAndGet();
        queuedCount.incrementAndGet();

        Thread.ofVirtual()
                .name(namePrefix, threadIdCounter.getAndIncrement())
                .start(() -> {
                    boolean acquired = false;
                    try {
                        if (semaphore != null) {
                            semaphore.acquire();
                            acquired = true;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        queuedCount.decrementAndGet();
                        signalTaskDone();
                        future.completeExceptionally(e);
                        return;
                    }

                    queuedCount.decrementAndGet();
                    activeCount.incrementAndGet();

                    try {
                        T result = task.call();
                        future.complete(result);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    } finally {
                        activeCount.decrementAndGet();
                        completedCount.increment();
                        if (acquired) {
                            semaphore.release();
                        }
                        signalTaskDone();
                    }
                });

        return future;
    }

    /**
     * Submits a runnable task to run on a rate-limited virtual thread.
     *
     * @param task runnable task
     * @return {@link CompletableFuture} tracking task completion
     */
    public CompletableFuture<Void> run(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        return submit(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Submits multiple runnable tasks concurrently.
     *
     * @param tasks collection of runnables
     * @return list of futures
     */
    public List<CompletableFuture<Void>> runAll(Collection<Runnable> tasks) {
        Objects.requireNonNull(tasks, "tasks must not be null");
        List<CompletableFuture<Void>> futures = new ArrayList<>(tasks.size());
        for (Runnable r : tasks) {
            futures.add(run(r));
        }
        return futures;
    }

    /**
     * Submits multiple callable tasks concurrently.
     *
     * @param tasks collection of callables
     * @param <T>   result type
     * @return list of futures
     */
    public <T> List<CompletableFuture<T>> submitAll(Collection<Callable<T>> tasks) {
        Objects.requireNonNull(tasks, "tasks must not be null");
        List<CompletableFuture<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> c : tasks) {
            futures.add(submit(c));
        }
        return futures;
    }

    /**
     * Blocks until all currently in-flight and queued tasks have completed.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void awaitAll() throws InterruptedException {
        finishLock.lock();
        try {
            while (inFlightCount.get() > 0) {
                allFinished.await();
            }
        } finally {
            finishLock.unlock();
        }
    }

    /**
     * Blocks until all currently in-flight tasks have completed, or until timeout elapses.
     *
     * @param timeout maximum duration to wait
     * @return true if all tasks finished, false if timeout elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitAll(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout must not be null");
        long remainingNanos = timeout.toNanos();

        finishLock.lock();
        try {
            while (inFlightCount.get() > 0) {
                if (remainingNanos <= 0) {
                    return false;
                }
                remainingNanos = allFinished.awaitNanos(remainingNanos);
            }
            return true;
        } finally {
            finishLock.unlock();
        }
    }

    /**
     * Number of tasks actively executing inside the rate-limited boundary.
     */
    public int getActiveCount() {
        return activeCount.get();
    }

    /**
     * Number of tasks waiting for a concurrency permit.
     */
    public int getQueuedCount() {
        return queuedCount.get();
    }

    /**
     * Total number of tasks that have finished execution (both successes and failures).
     */
    public long getCompletedCount() {
        return completedCount.sum();
    }

    /**
     * Maximum concurrency permit limit, or {@link Integer#MAX_VALUE} if unbounded.
     */
    public int getMaxConcurrency() {
        return (maxConcurrency > 0) ? maxConcurrency : Integer.MAX_VALUE;
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                awaitAll(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void signalTaskDone() {
        if (inFlightCount.decrementAndGet() == 0) {
            finishLock.lock();
            try {
                allFinished.signalAll();
            } finally {
                finishLock.unlock();
            }
        }
    }

    private void checkNotClosed() {
        if (closed.get()) {
            throw new RejectedExecutionException("VirtualThreadRunner is already closed");
        }
    }

    public static class Builder {
        private int maxConcurrency = 0;
        private boolean fair = false;
        private String namePrefix = "vthread-runner-";

        private Builder() {}

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder fair(boolean fair) {
            this.fair = fair;
            return this;
        }

        public Builder namePrefix(String namePrefix) {
            this.namePrefix = (namePrefix != null && !namePrefix.isBlank()) ? namePrefix : "vthread-runner-";
            return this;
        }

        public VirtualThreadRunner build() {
            return new VirtualThreadRunner(this);
        }
    }
}
