package com.msp.crondag;

/**
 * Thrown when a task is skipped because one or more of its upstream dependencies failed.
 */
public class UpstreamFailedException extends RuntimeException {

    private final String failedTaskId;

    public UpstreamFailedException(String failedTaskId, Throwable cause) {
        super("Upstream dependency task '" + failedTaskId + "' failed: " + (cause != null ? cause.getMessage() : "unknown"), cause);
        this.failedTaskId = failedTaskId;
    }

    public String getFailedTaskId() {
        return failedTaskId;
    }
}
