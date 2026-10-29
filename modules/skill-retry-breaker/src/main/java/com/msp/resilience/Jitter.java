package com.msp.resilience;

/**
 * Jitter strategy to prevent thundering herd problems during retries.
 */
public enum Jitter {
    /**
     * No jitter: uses exact computed backoff delay.
     */
    NONE,

    /**
     * Full jitter: random delay between 0 and computed backoff delay.
     */
    FULL,

    /**
     * Equal jitter: half delay fixed, other half randomized between 0 and half delay.
     */
    EQUAL
}
