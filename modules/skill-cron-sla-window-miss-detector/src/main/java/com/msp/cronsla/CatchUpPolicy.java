package com.msp.cronsla;

/**
 * Policy governing how missed or SLA-breached scheduled executions should be remediated.
 */
public enum CatchUpPolicy {
    /**
     * Trigger a single immediate catch-up execution for the most recent missed slot, collapsing older missed slots.
     */
    FIRE_ONCE_IMMEDIATELY,

    /**
     * Trigger an individual catch-up execution for every missed scheduled slot in chronological order.
     */
    FIRE_ALL_MISSED,

    /**
     * Do not execute missed slots; wait for the next future scheduled occurrence.
     */
    SKIP_TO_NEXT,

    /**
     * Emit an SLA alert trigger without scheduling a catch-up job run.
     */
    ALERT_ONLY
}
