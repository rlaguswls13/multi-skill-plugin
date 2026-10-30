package com.msp.stopwatch;

import java.util.Objects;

/**
 * Represents a single timed lap/segment in the split stopwatch.
 *
 * @param name                    name or label of this segment
 * @param lapDurationNanos        time elapsed during this specific lap
 * @param cumulativeDurationNanos total time elapsed from stopwatch start until this lap
 */
public record SplitLap(
        String name,
        long lapDurationNanos,
        long cumulativeDurationNanos
) {
    public SplitLap {
        Objects.requireNonNull(name, "name must not be null");
    }

    public double lapMillis() {
        return lapDurationNanos / 1_000_000.0;
    }

    public double cumulativeMillis() {
        return cumulativeDurationNanos / 1_000_000.0;
    }

    public double percentage(long totalNanos) {
        if (totalNanos <= 0) return 0.0;
        return (lapDurationNanos * 100.0) / totalNanos;
    }
}
