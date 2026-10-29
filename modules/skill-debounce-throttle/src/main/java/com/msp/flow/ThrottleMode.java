package com.msp.flow;

/**
 * Execution strategy for rate-limiting action throttlers.
 */
public enum ThrottleMode {
    /**
     * Leading edge (default): executes immediately on first call, suppresses all invocations
     * within the rate-limiting interval.
     */
    LEADING,

    /**
     * Trailing edge: defers execution until the interval has elapsed, executing the most
     * recently submitted action at the end of the window.
     */
    TRAILING,

    /**
     * Leading and trailing edge: executes immediately on first call. If any subsequent
     * calls arrive during the interval, the most recent call is executed once the interval ends.
     */
    LEADING_AND_TRAILING
}
