package com.msp.pqueue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * In-memory asynchronous worker pool consuming from a {@link PriorityWorkQueue}.
 * <p>
 * Supports virtual threads or platform threads, dynamic pause/resume,
 * failure handling, and clean graceful termination.
 * </p>
 *
 * @param <T> work item type
 */
public class WorkerPool<T> implements AutoCloseable {

    private final PriorityWorkQueue<T> queue;
    private final Consumer<T> itemConsumer;
    private final BiConsumer<T, Throwable> errorHandler;
    private final int workerCount;
    private final boolean useVirtualThreads;
    private final String threadNamePrefix;

    private final List<Thread> workerThreads = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final LongAdder processedCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private CountDownLatch terminationLatch;

    private WorkerPool(Builder<T> builder) {
        this.queue = builder.queue;
        this.itemConsumer = builder.itemConsumer;
        this.errorHandler = builder.errorHandler;
        this.workerCount = builder.workerCount;
        this.useVirtualThreads = builder.useVirtualThreads;
        this.threadNamePrefix = builder.threadNamePrefix;
    }

    public static <T> Builder<T> builder(PriorityWorkQueue<T> queue, Consumer<T> itemConsumer) {
        return new Builder<>(queue, itemConsumer);
    }

    /**
     * Starts the worker pool threads.
     *
     * @return this worker pool
     */
    public synchronized WorkerPool<T> start() {
        if (running.compareAndSet(false, true)) {
            terminationLatch = new CountDownLatch(workerCount);
            workerThreads.clear();

            for (int i = 0; i < workerCount; i++) {
                String threadName = threadNamePrefix + "-" + i;
                Runnable task = this::workerLoop;
                Thread thread;
                if (useVirtualThreads) {
                    thread = Thread.ofVirtual().name(threadName).unstarted(task);
                } else {
                    thread = new Thread(task, threadName);
                    thread.setDaemon(true);
                }
                workerThreads.add(thread);
                thread.start();
            }
        }
        return this;
    }

    /**
     * Stops the worker pool and signals all worker threads to terminate.
     */
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            for (Thread thread : workerThreads) {
                thread.interrupt();
            }
        }
    }

    /**
     * Waits for all worker threads to complete within the given timeout duration.
     *
     * @param timeout maximum time to wait
     * @return true if all workers terminated, false if timeout elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        CountDownLatch latch = this.terminationLatch;
        if (latch == null) {
            return true;
        }
        return latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Pauses task processing. Workers will wait without draining items until resumed.
     */
    public void pause() {
        paused.set(true);
    }

    /**
     * Resumes task processing after being paused.
     */
    public void resume() {
        paused.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    public int getActiveWorkerCount() {
        return activeWorkers.get();
    }

    public long getProcessedCount() {
        return processedCount.sum();
    }

    public long getFailureCount() {
        return failureCount.sum();
    }

    @Override
    public void close() {
        stop();
        try {
            awaitTermination(Duration.ofSeconds(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void workerLoop() {
        try {
            while (running.get()) {
                if (paused.get()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }

                T item;
                try {
                    item = queue.poll(Duration.ofMillis(100));
                } catch (InterruptedException e) {
                    break;
                }

                if (item != null) {
                    activeWorkers.incrementAndGet();
                    try {
                        itemConsumer.accept(item);
                        processedCount.increment();
                    } catch (Throwable t) {
                        failureCount.increment();
                        if (errorHandler != null) {
                            try {
                                errorHandler.accept(item, t);
                            } catch (Throwable ignored) {
                            }
                        }
                    } finally {
                        activeWorkers.decrementAndGet();
                    }
                }
            }
        } finally {
            if (terminationLatch != null) {
                terminationLatch.countDown();
            }
        }
    }

    public static class Builder<T> {
        private final PriorityWorkQueue<T> queue;
        private final Consumer<T> itemConsumer;
        private BiConsumer<T, Throwable> errorHandler;
        private int workerCount = Math.max(2, Runtime.getRuntime().availableProcessors());
        private boolean useVirtualThreads = true;
        private String threadNamePrefix = "pqueue-worker";

        private Builder(PriorityWorkQueue<T> queue, Consumer<T> itemConsumer) {
            this.queue = Objects.requireNonNull(queue, "queue must not be null");
            this.itemConsumer = Objects.requireNonNull(itemConsumer, "itemConsumer must not be null");
        }

        public Builder<T> workers(int workerCount) {
            if (workerCount <= 0) {
                throw new IllegalArgumentException("workerCount must be positive: " + workerCount);
            }
            this.workerCount = workerCount;
            return this;
        }

        public Builder<T> useVirtualThreads(boolean useVirtualThreads) {
            this.useVirtualThreads = useVirtualThreads;
            return this;
        }

        public Builder<T> threadNamePrefix(String prefix) {
            this.threadNamePrefix = (prefix != null && !prefix.isBlank()) ? prefix : "pqueue-worker";
            return this;
        }

        public Builder<T> onError(BiConsumer<T, Throwable> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        public WorkerPool<T> build() {
            return new WorkerPool<>(this);
        }
    }
}
