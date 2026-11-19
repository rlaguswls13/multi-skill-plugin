package com.msp.blackout;

/**
 * Policy governing how a scheduled execution behaves when it falls inside a blackout window.
 */
public enum BlackoutPolicy {
    /** Shift execution to the earliest allowed instant after all active/overlapping blackout windows end. */
    DELAY,
    /** Skip the execution (return {@link java.util.Optional#empty()}). */
    SKIP,
    /** Reject the execution by throwing {@link BlackoutConflictException}. */
    REJECT
}
