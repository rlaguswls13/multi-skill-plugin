package com.msp.priorexec;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Priority and TTL-based concurrency task executor for background async workloads.
 * Tasks are prioritized from 1 (lowest) to 10 (highest, default 5).
 * Queued tasks that exceed their Time-To-Live (TTL) deadline before starting
 * are completed exceptionally with {@link TaskExpiredException}.
 */
public class PriorityTaskExecutor implements AutoCloseable {

    public static final int DEFAULT_PRIORITY = 5;
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 10;

    private final ThreadPoolExecutor pool;
    private final AtomicLong sequenceGenerator = new AtomicLong(0);
    private final AtomicLong submittedCount = new AtomicLong(0);
    private final LongAdder completedCount = new LongAdder();
    private final LongAdder expiredCount = new LongAdder();

    public PriorityTaskExecutor(int corePoolSize, int maxPoolSize, Duration keepAliveTime, ThreadFactory threadFactory) {
        if (corePoolSize < 1 || maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("Invalid pool sizes: core=" + corePoolSize + ", max=" + maxPoolSize);
        }
        Objects.requireNonNull(keepAliveTime, "keepAliveTime must not be null");
        Objects.requireNonNull(threadFactory, "threadFactory must not be null");

        this.pool = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime.toMillis(),
                TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(),
                threadFactory
        );
        this.pool.prestartAllCoreThreads();
    }

    public static PriorityTaskExecutor newFixedThreadPool(int nThreads) {
        ThreadFactory factory = Executors.defaultThreadFactory();
        return new PriorityTaskExecutor(nThreads, nThreads, Duration.ZERO, factory);
    }

    public static PriorityTaskExecutor newSingleThreadExecutor() {
        return newFixedThreadPool(1);
    }

    /**
     * Submits a value-returning task with a custom priority and optional TTL.
     */
    public <T> CompletableFuture<T> submit(Callable<T> task, int priority, Duration ttl) {
        Objects.requireNonNull(task, "task must not be null");
        if (pool.isShutdown()) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Executor has been shut down"));
            return failed;
        }

        long seq = sequenceGenerator.incrementAndGet();
        submittedCount.incrementAndGet();

        CompletableFuture<T> future = new CompletableFuture<>();
        PriorityTask<T> priorityTask = new PriorityTask<>(
                task,
                future,
                priority,
                seq,
                ttl,
                completedCount,
                expiredCount
        );

        pool.execute(priorityTask);
        return future;
    }

    public <T> CompletableFuture<T> submit(Callable<T> task, int priority) {
        return submit(task, priority, null);
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return submit(task, DEFAULT_PRIORITY, null);
    }

    /**
     * Submits a Runnable task with a custom priority and optional TTL.
     */
    public CompletableFuture<Void> run(Runnable task, int priority, Duration ttl) {
        Objects.requireNonNull(task, "task must not be null");
        Callable<Void> callable = () -> {
            task.run();
            return null;
        };
        return submit(callable, priority, ttl);
    }

    public CompletableFuture<Void> run(Runnable task, int priority) {
        return run(task, priority, null);
    }

    public CompletableFuture<Void> run(Runnable task) {
        return run(task, DEFAULT_PRIORITY, null);
    }

    public int getActiveCount() {
        return pool.getActiveCount();
    }

    public int getQueueDepth() {
        return pool.getQueue().size();
    }

    public long getExpiredTaskCount() {
        return expiredCount.sum();
    }

    public long getCompletedTaskCount() {
        return completedCount.sum();
    }

    public long getSubmittedTaskCount() {
        return submittedCount.get();
    }

    public void shutdown() {
        pool.shutdown();
    }

    public List<Runnable> shutdownNow() {
        return pool.shutdownNow();
    }

    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        return pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean isShutdown() {
        return pool.isShutdown();
    }

    public boolean isTerminated() {
        return pool.isTerminated();
    }

    @Override
    public void close() {
        shutdown();
        try {
            if (!awaitTermination(Duration.ofSeconds(5))) {
                shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdownNow();
        }
    }
}
