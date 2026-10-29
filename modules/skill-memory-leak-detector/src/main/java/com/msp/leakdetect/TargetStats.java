package com.msp.leakdetect;

import java.time.Instant;
import java.util.Objects;

/**
 * Snapshot statistics of a monitored collection target.
 */
public record TargetStats(
        String name,
        int currentSize,
        int initialSize,
        int sampleCount,
        int consecutiveGrowthCount,
        double velocityPerSecond,
        Instant lastSampledAt
) {
    public TargetStats {
        Objects.requireNonNull(name, "name must not be null");
        lastSampledAt = lastSampledAt != null ? lastSampledAt : Instant.now();
    }
}
