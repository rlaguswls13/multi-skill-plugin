package com.msp.flow;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe high-frequency action debouncer supporting trailing (default) and leading execution edges.
 * <p>
 * Standard Trailing Debounce postpones execution of an action until a quiet period of duration has passed
 * without further invocations. Leading Debounce executes the action immediately upon the first trigger,
 * then suppresses execution until a quiet period without invocations has passed.
 */
public final class Debouncer implements AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final ConcurrentHashMap<Object, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    public Debouncer() {
        this(Executors.newScheduledThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                Thread.ofPlatform().daemon().name("debouncer-", 0).factory()
        ), true);
    }

    public Debouncer(ScheduledExecutorService scheduler) {
        this(scheduler, false);
    }

    public Debouncer(ScheduledExecutorService scheduler, boolean ownsScheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.ownsScheduler = ownsScheduler;
    }

    /**
     * Debounces an action using default trailing-edge mode.
     *
     * @param key    unique identifier for grouping actions
     * @param delay  quiet-period duration
     * @param action the task to run
     */
    public void debounce(Object key, Duration delay, Runnable action) {
        debounce(key, delay, DebounceMode.TRAILING, action);
    }

    /**
     * Debounces an action using the specified mode.
     *
     * @param key    unique identifier for grouping actions
     * @param delay  quiet-period duration
     * @param mode   {@link DebounceMode#TRAILING} or {@link DebounceMode#LEADING}
     * @param action the task to run
     */
    public void debounce(Object key, Duration delay, DebounceMode mode, Runnable action) {
        checkNotClosed();
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(delay, "delay must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(action, "action must not be null");

        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative: " + delay);
        }

        long delayNanos = Math.max(0, delay.toNanos());

        if (mode == DebounceMode.TRAILING) {
            scheduleTrailing(key, delayNanos, action);
        } else {
            scheduleLeading(key, delayNanos, action);
        }
    }

    private void scheduleTrailing(Object key, long delayNanos, Runnable action) {
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        tasks.compute(key, (k, existing) -> {
            if (existing != null) {
                existing.cancel(false);
            }
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                try {
                    action.run();
                } finally {
                    tasks.computeIfPresent(key, (currKey, currFuture) ->
                            currFuture == futureRef.get() ? null : currFuture);
                }
            }, delayNanos, TimeUnit.NANOSECONDS);
            futureRef.set(future);
            return future;
        });
    }

    private void scheduleLeading(Object key, long delayNanos, Runnable action) {
        AtomicBoolean shouldRun = new AtomicBoolean(false);
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        tasks.compute(key, (k, existing) -> {
            if (existing == null) {
                shouldRun.set(true);
            } else {
                existing.cancel(false);
            }
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                tasks.computeIfPresent(key, (currKey, currFuture) ->
                        currFuture == futureRef.get() ? null : currFuture);
            }, delayNanos, TimeUnit.NANOSECONDS);
            futureRef.set(future);
            return future;
        });

        if (shouldRun.get()) {
            action.run();
        }
    }

    /**
     * Cancels any pending debounced action for the given key.
     *
     * @param key the task key
     * @return true if a pending task was cancelled
     */
    public boolean cancel(Object key) {
        Objects.requireNonNull(key, "key must not be null");
        ScheduledFuture<?> future = tasks.remove(key);
        if (future != null) {
            return future.cancel(false);
        }
        return false;
    }

    /**
     * Cancels all pending debounced tasks.
     */
    public void cancelAll() {
        tasks.forEach((key, future) -> {
            if (future != null) {
                future.cancel(false);
            }
        });
        tasks.clear();
    }

    /**
     * Checks if there is a pending debounced task or quiet window for the given key.
     */
    public boolean hasPending(Object key) {
        Objects.requireNonNull(key, "key must not be null");
        ScheduledFuture<?> future = tasks.get(key);
        return future != null && !future.isDone();
    }

    /**
     * Returns the count of keys currently in debounce pending state.
     */
    public int pendingCount() {
        return (int) tasks.values().stream().filter(f -> !f.isDone()).count();
    }

    public boolean isClosed() {
        return closed;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Debouncer has been closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            cancelAll();
            if (ownsScheduler) {
                scheduler.shutdownNow();
            }
        }
    }
}
