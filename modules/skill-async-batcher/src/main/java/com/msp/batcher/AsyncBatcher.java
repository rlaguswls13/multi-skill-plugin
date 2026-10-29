package com.msp.batcher;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Micro-batching buffer that groups incoming items by batch size or max wait duration.
 * Thread-safe for multiple producers and guarantees thread-safe, orderly delivery to consumers.
 *
 * @param <T> item type
 */
public final class AsyncBatcher<T> implements AutoCloseable {

    private final int maxBatchSize;
    private final Duration maxWaitDuration;
    private final Consumer<List<T>> consumer;
    private final BiConsumer<List<T>, Throwable> errorHandler;

    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final ExecutorService workerExecutor;
    private final boolean ownsWorkerExecutor;

    private final ReentrantLock lock = new ReentrantLock();
    private final List<T> buffer;
    private ScheduledFuture<?> timeoutTask = null;
    private CompletableFuture<Void> lastDispatchFuture = CompletableFuture.completedFuture(null);
    private volatile boolean closed = false;

    private final AtomicLong totalSubmitted = new AtomicLong(0);
    private final AtomicLong totalBatches = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);

    private AsyncBatcher(Builder<T> builder) {
        this.maxBatchSize = builder.maxBatchSize;
        this.maxWaitDuration = builder.maxWaitDuration;
        this.consumer = builder.consumer;
        this.errorHandler = builder.errorHandler;
        this.buffer = new ArrayList<>(this.maxBatchSize);

        if (builder.scheduler != null) {
            this.scheduler = builder.scheduler;
            this.ownsScheduler = false;
        } else {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().daemon().name("async-batcher-timer-", 0).factory()
            );
            this.ownsScheduler = true;
        }

        if (builder.workerExecutor != null) {
            this.workerExecutor = builder.workerExecutor;
            this.ownsWorkerExecutor = false;
        } else {
            this.workerExecutor = Executors.newSingleThreadExecutor(
                    Thread.ofPlatform().daemon().name("async-batcher-worker-", 0).factory()
            );
            this.ownsWorkerExecutor = true;
        }
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Submits a single item into the batching buffer.
     * Automatically triggers a batch dispatch if threshold is reached.
     *
     * @param item the element to submit
     */
    public void submit(T item) {
        Objects.requireNonNull(item, "item must not be null");
        lock.lock();
        try {
            checkNotClosed();
            buffer.add(item);
            totalSubmitted.incrementAndGet();

            if (buffer.size() >= maxBatchSize) {
                dispatchBatchLocked();
            } else if (buffer.size() == 1) {
                scheduleTimeoutLocked();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Submits a collection of items into the buffer.
     *
     * @param items items to submit
     */
    public void submitAll(Collection<T> items) {
        Objects.requireNonNull(items, "items collection must not be null");
        if (items.isEmpty()) {
            return;
        }
        lock.lock();
        try {
            checkNotClosed();
            for (T item : items) {
                Objects.requireNonNull(item, "item in items must not be null");
                buffer.add(item);
                totalSubmitted.incrementAndGet();
                if (buffer.size() >= maxBatchSize) {
                    dispatchBatchLocked();
                }
            }
            if (!buffer.isEmpty() && timeoutTask == null) {
                scheduleTimeoutLocked();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Manually triggers flush of all currently buffered items and blocks until the consumer finishes.
     */
    public void flush() {
        CompletableFuture<Void> waitFuture;
        lock.lock();
        try {
            while (!buffer.isEmpty()) {
                dispatchBatchLocked();
            }
            waitFuture = lastDispatchFuture;
        } finally {
            lock.unlock();
        }
        if (waitFuture != null) {
            waitFuture.join();
        }
    }

    /**
     * Triggers flush of all currently buffered items asynchronously.
     *
     * @return CompletableFuture that completes when all currently queued batches finish execution
     */
    public CompletableFuture<Void> flushAsync() {
        lock.lock();
        try {
            while (!buffer.isEmpty()) {
                dispatchBatchLocked();
            }
            return lastDispatchFuture;
        } finally {
            lock.unlock();
        }
    }

    private void scheduleTimeoutLocked() {
        if (timeoutTask != null && !timeoutTask.isDone()) {
            return;
        }
        timeoutTask = scheduler.schedule(() -> {
            lock.lock();
            try {
                if (!buffer.isEmpty()) {
                    dispatchBatchLocked();
                }
            } finally {
                lock.unlock();
            }
        }, maxWaitDuration.toNanos(), TimeUnit.NANOSECONDS);
    }

    private void dispatchBatchLocked() {
        if (buffer.isEmpty()) {
            return;
        }
        int takeCount = Math.min(buffer.size(), maxBatchSize);
        List<T> batch = new ArrayList<>(buffer.subList(0, takeCount));
        buffer.subList(0, takeCount).clear();

        if (timeoutTask != null) {
            timeoutTask.cancel(false);
            timeoutTask = null;
        }

        if (!buffer.isEmpty()) {
            scheduleTimeoutLocked();
        }

        List<T> immutableBatch = Collections.unmodifiableList(batch);
        lastDispatchFuture = lastDispatchFuture.handle((prev, err) -> null)
                .thenRunAsync(() -> {
                    try {
                        consumer.accept(immutableBatch);
                        totalBatches.incrementAndGet();
                        totalProcessed.addAndGet(immutableBatch.size());
                    } catch (Throwable t) {
                        if (errorHandler != null) {
                            errorHandler.accept(immutableBatch, t);
                        }
                    }
                }, workerExecutor);
    }

    public int pendingCount() {
        lock.lock();
        try {
            return buffer.size();
        } finally {
            lock.unlock();
        }
    }

    public long totalSubmittedCount() {
        return totalSubmitted.get();
    }

    public long totalBatchesProcessed() {
        return totalBatches.get();
    }

    public long totalItemsProcessed() {
        return totalProcessed.get();
    }

    public boolean isClosed() {
        return closed;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("AsyncBatcher is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        flush();
        lock.lock();
        try {
            closed = true;
            if (timeoutTask != null) {
                timeoutTask.cancel(false);
                timeoutTask = null;
            }
        } finally {
            lock.unlock();
        }
        if (ownsScheduler) {
            scheduler.shutdown();
        }
        if (ownsWorkerExecutor) {
            workerExecutor.shutdown();
        }
    }

    /**
     * Builder for {@link AsyncBatcher}.
     *
     * @param <T> item type
     */
    public static final class Builder<T> {
        private int maxBatchSize = 100;
        private Duration maxWaitDuration = Duration.ofMillis(50);
        private Consumer<List<T>> consumer;
        private BiConsumer<List<T>, Throwable> errorHandler;
        private ScheduledExecutorService scheduler;
        private ExecutorService workerExecutor;

        private Builder() {}

        public Builder<T> maxBatchSize(int maxBatchSize) {
            if (maxBatchSize <= 0) {
                throw new IllegalArgumentException("maxBatchSize must be > 0: " + maxBatchSize);
            }
            this.maxBatchSize = maxBatchSize;
            return this;
        }

        public Builder<T> maxWaitDuration(Duration maxWaitDuration) {
            Objects.requireNonNull(maxWaitDuration, "maxWaitDuration must not be null");
            if (maxWaitDuration.isNegative() || maxWaitDuration.isZero()) {
                throw new IllegalArgumentException("maxWaitDuration must be positive: " + maxWaitDuration);
            }
            this.maxWaitDuration = maxWaitDuration;
            return this;
        }

        public Builder<T> consumer(Consumer<List<T>> consumer) {
            this.consumer = Objects.requireNonNull(consumer, "consumer must not be null");
            return this;
        }

        public Builder<T> errorHandler(BiConsumer<List<T>, Throwable> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        public Builder<T> scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public Builder<T> workerExecutor(ExecutorService workerExecutor) {
            this.workerExecutor = workerExecutor;
            return this;
        }

        public AsyncBatcher<T> build() {
            if (consumer == null) {
                throw new IllegalStateException("consumer must be configured before building AsyncBatcher");
            }
            return new AsyncBatcher<>(this);
        }
    }
}
