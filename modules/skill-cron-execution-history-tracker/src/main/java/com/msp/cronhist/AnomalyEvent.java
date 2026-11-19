package com.msp.cronhist;

import java.time.Instant;
import java.util.Objects;

/**
 * Event triggered when an execution anomaly is detected.
 */
public record AnomalyEvent(
        AnomalyType type,
        String taskId,
        String message,
        Instant detectedAt,
        ExecutionRecord triggeringRecord
) {
    public AnomalyEvent {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(taskId, "taskId cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(detectedAt, "detectedAt cannot be null");
        Objects.requireNonNull(triggeringRecord, "triggeringRecord cannot be null");
    }
}
