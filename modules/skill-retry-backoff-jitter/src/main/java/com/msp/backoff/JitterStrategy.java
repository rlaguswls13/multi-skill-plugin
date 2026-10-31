package com.msp.backoff;

/**
 * Standard jitter strategies for exponential backoff retry algorithms.
 * Based on AWS and Google distributed systems architecture patterns to prevent "Thundering Herd" stampedes.
 */
public enum JitterStrategy {
    /**
     * No jitter: deterministic exponential backoff (min(maxDelay, base * multiplier^attempt)).
     */
    NONE,

    /**
     * Full jitter: sleep = rand(0, min(maxDelay, base * multiplier^attempt)).
     * Spreads requests evenly from 0 up to current ceiling.
     */
    FULL_JITTER,

    /**
     * Equal jitter: sleep = temp / 2 + rand(0, temp / 2).
     * Keeps at least half the backoff time while randomizing the remaining half.
     */
    EQUAL_JITTER,

    /**
     * Decorrelated jitter: sleep = min(maxDelay, rand(base, previousSleep * 3)).
     * Avoids clusters of retries across multiple iterations without depending solely on attempt index.
     */
    DECORRELATED_JITTER
}
