package com.msp.metrics;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Thread-safe stopwatch and performance profiler measuring latency distributions and throughput.
 */
public class MetricsTimer {

    private final ConcurrentHashMap<String, MetricTracker> trackers = new ConcurrentHashMap<>();
    private final int defaultReservoirSize;

    public MetricsTimer() {
        this(2048);
    }

    public MetricsTimer(int defaultReservoirSize) {
        this.defaultReservoirSize = defaultReservoirSize;
    }

    public static MetricsTimer create() {
        return new MetricsTimer();
    }

    public static MetricsTimer create(int reservoirSize) {
        return new MetricsTimer(reservoirSize);
    }

    /**
     * Measures the execution time of a runnable action under the specified metric name.
     */
    public void time(String metricName, Runnable action) {
        Objects.requireNonNull(action, "action cannot be null");
        long start = System.nanoTime();
        try {
            action.run();
        } finally {
            recordNanos(metricName, System.nanoTime() - start);
        }
    }

    /**
     * Measures the execution time of a supplier action under the specified metric name and returns its result.
     */
    public <T> T time(String metricName, Supplier<T> action) {
        Objects.requireNonNull(action, "action cannot be null");
        long start = System.nanoTime();
        try {
            return action.get();
        } finally {
            recordNanos(metricName, System.nanoTime() - start);
        }
    }

    /**
     * Starts a new timer sample that can be stopped manually or via try-with-resources.
     */
    public TimerSample start(String metricName) {
        Objects.requireNonNull(metricName, "metricName cannot be null");
        long startNanos = System.nanoTime();
        AtomicBoolean stopped = new AtomicBoolean(false);

        return new TimerSample() {
            @Override
            public long stop() {
                if (stopped.compareAndSet(false, true)) {
                    long elapsed = System.nanoTime() - startNanos;
                    recordNanos(metricName, elapsed);
                    return elapsed;
                }
                return 0;
            }

            @Override
            public long elapsedNanos() {
                return System.nanoTime() - startNanos;
            }
        };
    }

    /**
     * Directly records a duration in nanoseconds for the given metric.
     */
    public void recordNanos(String metricName, long nanos) {
        Objects.requireNonNull(metricName, "metricName cannot be null");
        trackers.computeIfAbsent(metricName, k -> new MetricTracker(k, defaultReservoirSize))
                .record(nanos);
    }

    /**
     * Directly records a {@link Duration} for the given metric.
     */
    public void record(String metricName, Duration duration) {
        Objects.requireNonNull(duration, "duration cannot be null");
        recordNanos(metricName, duration.toNanos());
    }

    /**
     * Returns a point-in-time performance snapshot for the given metric.
     */
    public Optional<MetricSnapshot> snapshot(String metricName) {
        MetricTracker tracker = trackers.get(metricName);
        return tracker != null ? Optional.of(tracker.snapshot()) : Optional.empty();
    }

    /**
     * Returns snapshots for all tracked metrics mapped by metric name.
     */
    public Map<String, MetricSnapshot> snapshots() {
        Map<String, MetricSnapshot> map = new LinkedHashMap<>();
        trackers.forEach((name, tracker) -> map.put(name, tracker.snapshot()));
        return Collections.unmodifiableMap(map);
    }

    /**
     * Generates a formatted {@link MetricsReport} summarizing all tracked metrics.
     */
    public MetricsReport report() {
        return new MetricsReport(snapshots().values());
    }

    /**
     * Resets metrics data for a specific metric name.
     */
    public void reset(String metricName) {
        trackers.remove(metricName);
    }

    /**
     * Clears all tracked metrics.
     */
    public void reset() {
        trackers.clear();
    }
}
