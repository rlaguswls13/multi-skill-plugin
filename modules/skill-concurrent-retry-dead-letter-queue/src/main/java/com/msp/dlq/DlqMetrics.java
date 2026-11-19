package com.msp.dlq;

/**
 * Metric counters snapshot for dead-letter queue operations.
 *
 * @param totalQuarantined total number of messages ever enqueued into DLQ
 * @param activeQuarantined current number of messages in QUARANTINED state
 * @param replaySuccessCount number of messages successfully reprocessed
 * @param replayFailureCount number of reprocess attempts that threw exceptions
 * @param discardedCount number of messages explicitly discarded or purged
 */
public record DlqMetrics(
        long totalQuarantined,
        long activeQuarantined,
        long replaySuccessCount,
        long replayFailureCount,
        long discardedCount
) {}
