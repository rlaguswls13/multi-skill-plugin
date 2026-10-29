package com.msp.poolwatch;

/**
 * Reasons why a thread pool alert may be triggered.
 */
public enum AlertReason {
    /**
     * The work queue saturation ratio exceeded the configured threshold.
     */
    QUEUE_SATURATION,

    /**
     * Active worker threads ratio relative to maximumPoolSize exceeded the configured threshold.
     */
    ACTIVE_WORKER_SATURATION,

    /**
     * One or more tasks were rejected by the thread pool.
     */
    TASK_REJECTION
}
