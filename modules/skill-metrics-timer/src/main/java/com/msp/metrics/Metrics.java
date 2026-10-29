package com.msp.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Global static facade for metrics profiling and timing.
 */
public final class Metrics {

    private static final MetricsTimer DEFAULT_TIMER = MetricsTimer.create();

    private Metrics() {
    }

    public static MetricsTimer defaultTimer() {
        return DEFAULT_TIMER;
    }

    public static MetricsTimer newTimer() {
        return MetricsTimer.create();
    }

    public static MetricsTimer newTimer(int reservoirSize) {
        return MetricsTimer.create(reservoirSize);
    }

    public static void time(String metricName, Runnable action) {
        DEFAULT_TIMER.time(metricName, action);
    }

    public static <T> T time(String metricName, Supplier<T> action) {
        return DEFAULT_TIMER.time(metricName, action);
    }

    public static TimerSample start(String metricName) {
        return DEFAULT_TIMER.start(metricName);
    }

    public static void record(String metricName, Duration duration) {
        DEFAULT_TIMER.record(metricName, duration);
    }

    public static void recordNanos(String metricName, long nanos) {
        DEFAULT_TIMER.recordNanos(metricName, nanos);
    }

    public static Optional<MetricSnapshot> snapshot(String metricName) {
        return DEFAULT_TIMER.snapshot(metricName);
    }

    public static Map<String, MetricSnapshot> snapshots() {
        return DEFAULT_TIMER.snapshots();
    }

    public static MetricsReport report() {
        return DEFAULT_TIMER.report();
    }

    public static void reset() {
        DEFAULT_TIMER.reset();
    }
}
