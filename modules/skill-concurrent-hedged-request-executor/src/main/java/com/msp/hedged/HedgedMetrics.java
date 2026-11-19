package com.msp.hedged;

/**
 * Snapshot of hedged request execution telemetry metrics.
 *
 * @param primaryWins             Number of executions where the initial primary request won
 * @param hedgedWins              Number of executions where a secondary speculative hedged request won
 * @param totalHedgesFired        Total number of secondary speculative hedged requests launched
 * @param cancelledRedundantCalls Total number of in-flight redundant attempts cancelled after a winner completed
 */
public record HedgedMetrics(
        long primaryWins,
        long hedgedWins,
        long totalHedgesFired,
        long cancelledRedundantCalls
) {
}
