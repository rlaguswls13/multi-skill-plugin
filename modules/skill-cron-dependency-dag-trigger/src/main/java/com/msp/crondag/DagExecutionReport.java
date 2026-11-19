package com.msp.crondag;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * Summary execution report of a DAG pipeline run.
 *
 * @param isAllSuccess  True if all tasks completed with SUCCESS status
 * @param taskResults   Map of task ID to TaskExecutionResult
 * @param totalDuration Total wall-clock time elapsed
 */
public record DagExecutionReport(
        boolean isAllSuccess,
        Map<String, TaskExecutionResult<?>> taskResults,
        Duration totalDuration
) {
    public DagExecutionReport {
        taskResults = taskResults != null ? Collections.unmodifiableMap(taskResults) : Map.of();
    }

    @SuppressWarnings("unchecked")
    public <T> TaskExecutionResult<T> get(String taskId) {
        return (TaskExecutionResult<T>) taskResults.get(taskId);
    }

    public boolean hasFailedTasks() {
        return taskResults.values().stream().anyMatch(TaskExecutionResult::isFailed);
    }

    public boolean hasSkippedTasks() {
        return taskResults.values().stream().anyMatch(TaskExecutionResult::isSkipped);
    }
}
