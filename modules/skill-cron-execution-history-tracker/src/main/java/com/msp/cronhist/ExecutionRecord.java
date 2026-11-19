package com.msp.cronhist;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable record representing a single cron/scheduled task execution instance.
 */
public record ExecutionRecord(
        String taskId,
        Instant scheduledTime,
        Instant actualStartTime,
        Instant finishTime,
        boolean success,
        String failureMessage
) {
    public ExecutionRecord {
        Objects.requireNonNull(taskId, "taskId cannot be null");
        Objects.requireNonNull(scheduledTime, "scheduledTime cannot be null");
        Objects.requireNonNull(actualStartTime, "actualStartTime cannot be null");
        Objects.requireNonNull(finishTime, "finishTime cannot be null");
        if (finishTime.isBefore(actualStartTime)) {
            throw new IllegalArgumentException("finishTime cannot be before actualStartTime");
        }
    }

    public static ExecutionRecord of(String taskId, Instant scheduledTime, Instant actualStartTime, Instant finishTime, boolean success) {
        return new ExecutionRecord(taskId, scheduledTime, actualStartTime, finishTime, success, null);
    }

    public static ExecutionRecord failure(String taskId, Instant scheduledTime, Instant actualStartTime, Instant finishTime, String message) {
        return new ExecutionRecord(taskId, scheduledTime, actualStartTime, finishTime, false, message);
    }

    /**
     * Scheduling drift / latency (actualStartTime - scheduledTime).
     * Positive value indicates execution delay (thread starvation or scheduling lag).
     */
    public Duration drift() {
        return Duration.between(scheduledTime, actualStartTime);
    }

    /**
     * Task execution duration (finishTime - actualStartTime).
     */
    public Duration duration() {
        return Duration.between(actualStartTime, finishTime);
    }

    public Optional<String> getFailureMessage() {
        return Optional.ofNullable(failureMessage);
    }
}
