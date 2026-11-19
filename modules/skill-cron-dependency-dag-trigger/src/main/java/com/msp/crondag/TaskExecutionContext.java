package com.msp.crondag;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only runtime context provided to a task execution, containing outputs from upstream tasks.
 */
public class TaskExecutionContext {

    private final String currentTaskId;
    private final Map<String, Object> upstreamOutputs;

    public TaskExecutionContext(String currentTaskId, Map<String, Object> upstreamOutputs) {
        this.currentTaskId = Objects.requireNonNull(currentTaskId, "currentTaskId must not be null");
        this.upstreamOutputs = upstreamOutputs != null ? Collections.unmodifiableMap(upstreamOutputs) : Map.of();
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    /**
     * Retrieves the return value of an upstream dependency task.
     *
     * @param taskId Upstream task identifier
     * @param <T>    Expected return type
     * @return Output of the upstream task, or null if not produced
     */
    @SuppressWarnings("unchecked")
    public <T> T getResult(String taskId) {
        return (T) upstreamOutputs.get(taskId);
    }

    public boolean hasResult(String taskId) {
        return upstreamOutputs.containsKey(taskId);
    }
}
