package com.msp.poolwatch;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ThreadPoolExecutor health monitor and threshold alert watcher.
 * Computes queue saturation %, active worker ratio, rejection counts,
 * and notifies listeners when thresholds are breached.
 */
public final class ThreadPoolWatcher implements AutoCloseable {

    private final String poolName;
    private final ThreadPoolExecutor executor;
    private final ThresholdConfig thresholds;
    private final ThresholdAlertListener listener;

    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;

    private final MonitoredRejectedExecutionHandler monitoredHandler;
    private final AtomicLong lastReportedRejections = new AtomicLong(0);

    private ScheduledFuture<?> periodicTask = null;
    private volatile boolean closed = false;

    private ThreadPoolWatcher(Builder builder) {
        this.poolName = builder.poolName != null ? builder.poolName : "ThreadPool-" + System.identityHashCode(builder.executor);
        this.executor = Objects.requireNonNull(builder.executor, "executor must not be null");
        this.thresholds = builder.thresholds != null ? builder.thresholds : ThresholdConfig.DEFAULT;
        this.listener = builder.listener;

        RejectedExecutionHandler currentHandler = this.executor.getRejectedExecutionHandler();
        if (currentHandler instanceof MonitoredRejectedExecutionHandler mh) {
            this.monitoredHandler = mh;
        } else {
            // Automatically wrap handler with monitoring if requested
            if (builder.autoWrapRejectedHandler) {
                this.monitoredHandler = new MonitoredRejectedExecutionHandler(currentHandler, r -> {
                    if (this.listener != null) {
                        try {
                            PoolSnapshot snapshot = takeSnapshot();
                            this.listener.onThresholdExceeded(snapshot, AlertReason.TASK_REJECTION, snapshot.rejectedCount(), 0);
                        } catch (Throwable ignored) {
                        }
                    }
                });
                this.executor.setRejectedExecutionHandler(this.monitoredHandler);
            } else {
                this.monitoredHandler = null;
            }
        }

        if (builder.scheduler != null) {
            this.scheduler = builder.scheduler;
            this.ownsScheduler = false;
        } else {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().daemon().name("poolwatch-" + poolName + "-", 0).factory()
            );
            this.ownsScheduler = true;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Captures a point-in-time metrics snapshot of the monitored thread pool.
     *
     * @return {@link PoolSnapshot}
     */
    public PoolSnapshot takeSnapshot() {
        int corePoolSize = executor.getCorePoolSize();
        int maximumPoolSize = executor.getMaximumPoolSize();
        int poolSize = executor.getPoolSize();
        int activeCount = executor.getActiveCount();
        int largestPoolSize = executor.getLargestPoolSize();
        long taskCount = executor.getTaskCount();
        long completedTaskCount = executor.getCompletedTaskCount();

        int queueSize = executor.getQueue().size();
        int queueRemainingCapacity = executor.getQueue().remainingCapacity();

        double queueSaturation;
        if (queueRemainingCapacity == Integer.MAX_VALUE) {
            // Unbounded queue
            queueSaturation = queueSize > 0 ? (double) queueSize / (queueSize + 10_000) : 0.0;
        } else {
            long totalCapacity = (long) queueSize + (long) queueRemainingCapacity;
            queueSaturation = totalCapacity > 0 ? (double) queueSize / totalCapacity : 0.0;
        }

        double activeRatio = maximumPoolSize > 0 ? (double) activeCount / maximumPoolSize : 0.0;
        long rejectedCount = monitoredHandler != null ? monitoredHandler.getRejectionCount() : 0L;

        return new PoolSnapshot(
                poolName,
                corePoolSize,
                maximumPoolSize,
                poolSize,
                activeCount,
                largestPoolSize,
                taskCount,
                completedTaskCount,
                queueSize,
                queueRemainingCapacity,
                queueSaturation,
                activeRatio,
                rejectedCount,
                Instant.now()
        );
    }

    /**
     * Executes a single health check check, evaluating thresholds and firing listener alerts if exceeded.
     *
     * @return current snapshot
     */
    public PoolSnapshot checkOnce() {
        PoolSnapshot snapshot = takeSnapshot();
        evaluateThresholds(snapshot);
        return snapshot;
    }

    private void evaluateThresholds(PoolSnapshot snapshot) {
        if (listener == null) {
            return;
        }

        if (snapshot.isQueueSaturated(thresholds.queueSaturationThreshold())) {
            listener.onThresholdExceeded(snapshot, AlertReason.QUEUE_SATURATION,
                    snapshot.queueSaturationRatio(), thresholds.queueSaturationThreshold());
        }

        if (snapshot.isActiveWorkerSaturated(thresholds.activeThreadThreshold())) {
            listener.onThresholdExceeded(snapshot, AlertReason.ACTIVE_WORKER_SATURATION,
                    snapshot.activeThreadRatio(), thresholds.activeThreadThreshold());
        }

        long currentRejections = snapshot.rejectedCount();
        long previousRejections = lastReportedRejections.getAndSet(currentRejections);
        if (currentRejections > previousRejections) {
            listener.onThresholdExceeded(snapshot, AlertReason.TASK_REJECTION, currentRejections, previousRejections);
        }
    }

    /**
     * Starts periodic monitoring of the thread pool at the specified interval.
     *
     * @param interval check period
     */
    public synchronized void start(Duration interval) {
        Objects.requireNonNull(interval, "interval must not be null");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be positive: " + interval);
        }
        if (closed) {
            throw new IllegalStateException("ThreadPoolWatcher is closed");
        }
        if (periodicTask != null && !periodicTask.isDone()) {
            periodicTask.cancel(false);
        }
        long periodNanos = interval.toNanos();
        periodicTask = scheduler.scheduleAtFixedRate(this::checkOnce, periodNanos, periodNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Stops periodic monitoring.
     */
    public synchronized void stop() {
        if (periodicTask != null) {
            periodicTask.cancel(false);
            periodicTask = null;
        }
    }

    public boolean isRunning() {
        return periodicTask != null && !periodicTask.isDone();
    }

    public String getPoolName() {
        return poolName;
    }

    public ThresholdConfig getThresholds() {
        return thresholds;
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            stop();
            if (ownsScheduler) {
                scheduler.shutdown();
            }
        }
    }

    public static final class Builder {
        private String poolName;
        private ThreadPoolExecutor executor;
        private ThresholdConfig thresholds;
        private ThresholdAlertListener listener;
        private ScheduledExecutorService scheduler;
        private boolean autoWrapRejectedHandler = true;

        private Builder() {}

        public Builder name(String poolName) {
            this.poolName = poolName;
            return this;
        }

        public Builder executor(ThreadPoolExecutor executor) {
            this.executor = executor;
            return this;
        }

        public Builder thresholds(ThresholdConfig thresholds) {
            this.thresholds = thresholds;
            return this;
        }

        public Builder alertListener(ThresholdAlertListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public Builder autoWrapRejectedHandler(boolean autoWrap) {
            this.autoWrapRejectedHandler = autoWrap;
            return this;
        }

        public ThreadPoolWatcher build() {
            if (executor == null) {
                throw new IllegalStateException("executor must be specified");
            }
            return new ThreadPoolWatcher(this);
        }
    }
}
