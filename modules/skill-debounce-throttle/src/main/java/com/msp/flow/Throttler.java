package com.msp.flow;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe rate limiter that limits action invocations to at most once per duration interval.
 * Supports leading edge (immediate execution), trailing edge (deferred execution of latest action),
 * and leading-and-trailing combined mode.
 */
public final class Throttler implements AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final ConcurrentHashMap<Object, ThrottleState> states = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    private static final class ThrottleState {
        long lastExecutionNanos = 0L;
        Runnable pendingTrailingAction = null;
        ScheduledFuture<?> trailingFuture = null;
    }

    public Throttler() {
        this(Executors.newScheduledThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                Thread.ofPlatform().daemon().name("throttler-", 0).factory()
        ), true);
    }

    public Throttler(ScheduledExecutorService scheduler) {
        this(scheduler, false);
    }

    public Throttler(ScheduledExecutorService scheduler, boolean ownsScheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.ownsScheduler = ownsScheduler;
    }

    /**
     * Throttles an action using leading-edge mode (at most once per interval, runs immediately on leading edge).
     *
     * @param key      identifier for grouping actions
     * @param interval rate-limiting window
     * @param action   the task to run if not throttled
     * @return true if the action was executed, false if throttled/suppressed
     */
    public boolean throttle(Object key, Duration interval, Runnable action) {
        return throttle(key, interval, ThrottleMode.LEADING, action);
    }

    /**
     * Throttles an action using the specified mode.
     *
     * @param key      identifier for grouping actions
     * @param interval rate-limiting window
     * @param mode     {@link ThrottleMode}
     * @param action   the task to run
     * @return true if executed immediately (or accepted for trailing execution), false if suppressed
     */
    public boolean throttle(Object key, Duration interval, ThrottleMode mode, Runnable action) {
        checkNotClosed();
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(action, "action must not be null");

        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be positive: " + interval);
        }

        long intervalNanos = interval.toNanos();
        ThrottleState state = states.computeIfAbsent(key, k -> new ThrottleState());

        switch (mode) {
            case LEADING -> {
                return executeLeading(state, intervalNanos, action);
            }
            case TRAILING -> {
                return executeTrailingMode(key, state, intervalNanos, action);
            }
            case LEADING_AND_TRAILING -> {
                return executeLeadingAndTrailing(key, state, intervalNanos, action);
            }
            default -> throw new UnsupportedOperationException("Unknown mode: " + mode);
        }
    }

    private boolean executeLeading(ThrottleState state, long intervalNanos, Runnable action) {
        synchronized (state) {
            long now = System.nanoTime();
            if (state.lastExecutionNanos == 0L || (now - state.lastExecutionNanos) >= intervalNanos) {
                state.lastExecutionNanos = now;
                action.run();
                return true;
            }
            return false;
        }
    }

    private boolean executeTrailingMode(Object key, ThrottleState state, long intervalNanos, Runnable action) {
        synchronized (state) {
            state.pendingTrailingAction = action;
            if (state.trailingFuture == null || state.trailingFuture.isDone()) {
                state.trailingFuture = scheduler.schedule(
                        () -> runTrailingTask(key, state, intervalNanos),
                        intervalNanos,
                        TimeUnit.NANOSECONDS
                );
            }
            return true;
        }
    }

    private boolean executeLeadingAndTrailing(Object key, ThrottleState state, long intervalNanos, Runnable action) {
        synchronized (state) {
            long now = System.nanoTime();
            boolean canExecuteLeading = (state.lastExecutionNanos == 0L || (now - state.lastExecutionNanos) >= intervalNanos)
                    && (state.trailingFuture == null || state.trailingFuture.isDone());

            if (canExecuteLeading) {
                state.lastExecutionNanos = now;
                action.run();
                return true;
            } else {
                state.pendingTrailingAction = action;
                if (state.trailingFuture == null || state.trailingFuture.isDone()) {
                    long elapsed = now - state.lastExecutionNanos;
                    long remaining = Math.max(0, intervalNanos - elapsed);
                    state.trailingFuture = scheduler.schedule(
                            () -> runTrailingTask(key, state, intervalNanos),
                            remaining,
                            TimeUnit.NANOSECONDS
                    );
                }
                return false;
            }
        }
    }

    private void runTrailingTask(Object key, ThrottleState state, long intervalNanos) {
        Runnable taskToRun = null;
        synchronized (state) {
            taskToRun = state.pendingTrailingAction;
            state.pendingTrailingAction = null;
            state.trailingFuture = null;
            if (taskToRun != null) {
                state.lastExecutionNanos = System.nanoTime();
            }
        }
        if (taskToRun != null) {
            try {
                taskToRun.run();
            } catch (Throwable ignored) {
                // Keep background scheduler healthy
            }
        }
    }

    /**
     * Resets throttling tracking for the given key, allowing the next call to execute immediately.
     */
    public void reset(Object key) {
        Objects.requireNonNull(key, "key must not be null");
        ThrottleState state = states.remove(key);
        if (state != null) {
            synchronized (state) {
                if (state.trailingFuture != null) {
                    state.trailingFuture.cancel(false);
                    state.trailingFuture = null;
                }
                state.pendingTrailingAction = null;
                state.lastExecutionNanos = 0L;
            }
        }
    }

    /**
     * Resets throttling state for all keys.
     */
    public void resetAll() {
        states.keySet().forEach(this::reset);
    }

    /**
     * Checks if there is a pending trailing action scheduled for this key.
     */
    public boolean hasPendingTrailing(Object key) {
        Objects.requireNonNull(key, "key must not be null");
        ThrottleState state = states.get(key);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            return state.trailingFuture != null && !state.trailingFuture.isDone();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Throttler has been closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            resetAll();
            if (ownsScheduler) {
                scheduler.shutdownNow();
            }
        }
    }
}
