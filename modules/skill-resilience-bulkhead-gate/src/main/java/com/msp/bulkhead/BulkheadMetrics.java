package com.msp.bulkhead;

/**
 * Snapshot of real-time metrics for a {@link BulkheadGate}.
 *
 * @param availablePermits       Number of currently available execution permits.
 * @param maxConcurrentCalls     Configured maximum concurrent calls.
 * @param currentConcurrentCalls Number of currently active executions.
 * @param queueDepth             Number of callers currently waiting in line.
 * @param maxWaitQueueSize       Configured maximum wait queue depth.
 */
public record BulkheadMetrics(
        int availablePermits,
        int maxConcurrentCalls,
        int currentConcurrentCalls,
        int queueDepth,
        int maxWaitQueueSize
) {
}
