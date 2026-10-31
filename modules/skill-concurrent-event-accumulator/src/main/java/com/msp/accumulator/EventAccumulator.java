package com.msp.accumulator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Thread-safe batch event accumulator that buffers incoming events and flushes them
 * either when reaching a maximum batch size or when a maximum time interval elapses.
 *
 * @param <T> event payload type
 */
public class EventAccumulator<T> implements AutoCloseable {

    private final int maxBatchSize;
    private final Duration maxFlushInterval;
    private final Consumer<List<T>> flushConsumer;
    private final BiConsumer<Throwable, List<T>> errorHandler;

    private final List<T> buffer = new ArrayList<>();
    private final ReentrantLock bufferLock = new ReentrantLock();
    private final Object consumerExecutionLock = new Object();

    private final ScheduledExecutorService scheduler;
    private final ScheduledFuture<?> flushTask;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong totalAccumulated = new AtomicLong(0);
    private final AtomicLong totalFlushed = new AtomicLong(0);

    public EventAccumulator(
            int maxBatchSize,
            Duration maxFlushInterval,
            Consumer<List<T>> flushConsumer) {
        this(maxBatchSize, maxFlushInterval, flushConsumer, null);
    }

    public EventAccumulator(
            int maxBatchSize,
            Duration maxFlushInterval,
            Consumer<List<T>> flushConsumer,
            BiConsumer<Throwable, List<T>> errorHandler) {

        if (maxBatchSize <= 0) {
            throw new IllegalArgumentException("maxBatchSize must be positive: " + maxBatchSize);
        }
        Objects.requireNonNull(maxFlushInterval, "maxFlushInterval must not be null");
        if (maxFlushInterval.isNegative() || maxFlushInterval.isZero()) {
            throw new IllegalArgumentException("maxFlushInterval must be positive: " + maxFlushInterval);
        }
        this.maxBatchSize = maxBatchSize;
        this.maxFlushInterval = maxFlushInterval;
        this.flushConsumer = Objects.requireNonNull(flushConsumer, "flushConsumer must not be null");
        this.errorHandler = errorHandler;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "event-accumulator-" + System.identityHashCode(this));
            thread.setDaemon(true);
            return thread;
        });

        long intervalMillis = Math.max(1, maxFlushInterval.toMillis());
        this.flushTask = this.scheduler.scheduleWithFixedDelay(
                this::safeFlush,
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Adds an event to the accumulator buffer.
     * If the buffer reaches maxBatchSize, a flush is immediately triggered.
     *
     * @param event event payload
     */
    public void add(T event) {
        Objects.requireNonNull(event, "event must not be null");
        ensureNotClosed();

        List<T> batchToFlush = null;
        bufferLock.lock();
        try {
            ensureNotClosed();
            buffer.add(event);
            totalAccumulated.incrementAndGet();

            if (buffer.size() >= maxBatchSize) {
                batchToFlush = drainCurrentBufferUnderLock();
            }
        } finally {
            bufferLock.unlock();
        }

        if (batchToFlush != null && !batchToFlush.isEmpty()) {
            invokeConsumer(batchToFlush);
        }
    }

    /**
     * Adds a collection of events to the accumulator buffer.
     * Batches of maxBatchSize will be flushed sequentially.
     *
     * @param events events to add
     */
    public void addAll(Collection<? extends T> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        ensureNotClosed();

        List<List<T>> batchesToFlush = new ArrayList<>();
        bufferLock.lock();
        try {
            ensureNotClosed();
            for (T event : events) {
                if (event != null) {
                    buffer.add(event);
                    totalAccumulated.incrementAndGet();
                    if (buffer.size() >= maxBatchSize) {
                        batchesToFlush.add(drainCurrentBufferUnderLock());
                    }
                }
            }
        } finally {
            bufferLock.unlock();
        }

        for (List<T> batch : batchesToFlush) {
            invokeConsumer(batch);
        }
    }

    /**
     * Manually triggers a flush of any currently pending events.
     */
    public void flush() {
        List<T> batchToFlush;
        bufferLock.lock();
        try {
            batchToFlush = drainCurrentBufferUnderLock();
        } finally {
            bufferLock.unlock();
        }

        if (!batchToFlush.isEmpty()) {
            invokeConsumer(batchToFlush);
        }
    }

    private void safeFlush() {
        try {
            flush();
        } catch (Throwable t) {
            // Suppress exception in scheduler thread to keep scheduling alive
        }
    }

    private List<T> drainCurrentBufferUnderLock() {
        if (buffer.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> batch = new ArrayList<>(buffer);
        buffer.clear();
        return batch;
    }

    private void invokeConsumer(List<T> batch) {
        synchronized (consumerExecutionLock) {
            try {
                flushConsumer.accept(Collections.unmodifiableList(batch));
                totalFlushed.addAndGet(batch.size());
            } catch (Throwable t) {
                if (errorHandler != null) {
                    try {
                        errorHandler.accept(t, Collections.unmodifiableList(batch));
                    } catch (Throwable err) {
                        // ignore secondary error in handler
                    }
                } else {
                    throw t;
                }
            }
        }
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("EventAccumulator has already been closed");
        }
    }

    public int getPendingCount() {
        bufferLock.lock();
        try {
            return buffer.size();
        } finally {
            bufferLock.unlock();
        }
    }

    public long getTotalAccumulated() {
        return totalAccumulated.get();
    }

    public long getTotalFlushed() {
        return totalFlushed.get();
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            flushTask.cancel(false);
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Flush remaining pending items
            flush();
        }
    }
}
