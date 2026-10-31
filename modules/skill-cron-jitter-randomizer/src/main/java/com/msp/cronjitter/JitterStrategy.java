package com.msp.cronjitter;

/**
 * Strategy types for jitter application and exponential backoff randomization.
 */
public enum JitterStrategy {
    /**
     * Uniformly distributed random jitter in range [0, Max].
     */
    FULL,

    /**
     * Equal jitter: half of the interval is fixed, and the remaining half is random in [Max/2, Max].
     */
    EQUAL,

    /**
     * Decorrelated jitter where backoff is decorrelated from previous sleep values.
     */
    DECORRELATED
}
