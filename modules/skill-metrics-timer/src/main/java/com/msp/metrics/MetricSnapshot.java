package com.msp.metrics;

import java.time.Duration;

/**
 * Immutable snapshot of performance statistics for a specific metric at an instant in time.
 */
public record MetricSnapshot(
        String name,
        long count,
        long totalNanos,
        long minNanos,
        long maxNanos,
        double meanNanos,
        long p50Nanos,
        long p90Nanos,
        long p95Nanos,
        long p99Nanos,
        double tps
) {

    public Duration totalDuration() {
        return Duration.ofNanos(totalNanos);
    }

    public Duration minDuration() {
        return Duration.ofNanos(minNanos);
    }

    public Duration maxDuration() {
        return Duration.ofNanos(maxNanos);
    }

    public Duration meanDuration() {
        return Duration.ofNanos((long) meanNanos);
    }

    public Duration p50Duration() {
        return Duration.ofNanos(p50Nanos);
    }

    public Duration p90Duration() {
        return Duration.ofNanos(p90Nanos);
    }

    public Duration p95Duration() {
        return Duration.ofNanos(p95Nanos);
    }

    public Duration p99Duration() {
        return Duration.ofNanos(p99Nanos);
    }

    public double minMillis() {
        return minNanos / 1_000_000.0;
    }

    public double maxMillis() {
        return maxNanos / 1_000_000.0;
    }

    public double meanMillis() {
        return meanNanos / 1_000_000.0;
    }

    public double p50Millis() {
        return p50Nanos / 1_000_000.0;
    }

    public double p90Millis() {
        return p90Nanos / 1_000_000.0;
    }

    public double p95Millis() {
        return p95Nanos / 1_000_000.0;
    }

    public double p99Millis() {
        return p99Nanos / 1_000_000.0;
    }
}
