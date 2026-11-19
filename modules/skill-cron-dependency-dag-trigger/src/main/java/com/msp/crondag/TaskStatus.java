package com.msp.crondag;

/**
 * Lifecycle status of a task in the DAG pipeline.
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED_UPSTREAM_FAILED,
    CANCELLED
}
