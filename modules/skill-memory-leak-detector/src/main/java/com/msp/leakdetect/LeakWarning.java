package com.msp.leakdetect;

import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a potential collection memory leak is detected.
 */
public record LeakWarning(
        String targetName,
        WarningReason reason,
        int previousSize,
        int currentSize,
        int sampleCount,
        int consecutiveGrowthCount,
        double velocityPerSecond,
        Instant detectedAt,
        String message
) {
    public LeakWarning {
        Objects.requireNonNull(targetName, "targetName must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        detectedAt = detectedAt != null ? detectedAt : Instant.now();
        message = message != null ? message : "";
    }
}
