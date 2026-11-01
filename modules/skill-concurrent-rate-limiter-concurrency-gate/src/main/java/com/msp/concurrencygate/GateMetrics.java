package com.msp.concurrencygate;

/**
 * Snapshot of concurrency gate observability metrics.
 *
 * @param activeCount      Number of currently active/executing tasks
 * @param queueDepth       Number of tasks currently waiting in the gate queue
 * @param totalAcquired    Cumulative count of permits successfully acquired
 * @param totalRejected    Cumulative count of tasks rejected (due to capacity or timeout)
 * @param maxConcurrency   Maximum allowed concurrent executions
 * @param maxQueueCapacity Maximum allowed pending items in queue
 */
public record GateMetrics(
    int activeCount,
    int queueDepth,
    long totalAcquired,
    long totalRejected,
    int maxConcurrency,
    int maxQueueCapacity
) {
}
