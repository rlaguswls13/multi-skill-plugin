package com.msp.crondag;

import java.time.Duration;

/**
 * Result record for an individual DAG task execution.
 *
 * @param taskId            Task unique identifier
 * @param status            Execution status
 * @param value             Produced output value (if SUCCESS)
 * @param error             Encountered exception (if FAILED or SKIPPED)
 * @param executionDuration Time elapsed executing the task
 * @param <T>               Type of return value
 */
public record TaskExecutionResult<T>(
        String taskId,
        TaskStatus status,
        T value,
        Throwable error,
        Duration executionDuration
) {
    public boolean isSuccess() {
        return status == TaskStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == TaskStatus.FAILED;
    }

    public boolean isSkipped() {
        return status == TaskStatus.SKIPPED_UPSTREAM_FAILED;
    }

    public static <T> TaskExecutionResult<T> success(String taskId, T value, Duration duration) {
        return new TaskExecutionResult<>(taskId, TaskStatus.SUCCESS, value, null, duration);
    }

    public static <T> TaskExecutionResult<T> failed(String taskId, Throwable error, Duration duration) {
        return new TaskExecutionResult<>(taskId, TaskStatus.FAILED, null, error, duration);
    }

    public static <T> TaskExecutionResult<T> skipped(String taskId, UpstreamFailedException ex) {
        return new TaskExecutionResult<>(taskId, TaskStatus.SKIPPED_UPSTREAM_FAILED, null, ex, Duration.ZERO);
    }
}
