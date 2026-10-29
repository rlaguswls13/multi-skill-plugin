package com.msp.leakdetect;

/**
 * Reason code for memory leak detection warnings.
 */
public enum WarningReason {
    /**
     * Size has increased monotonically for N consecutive sampling intervals without decrease.
     */
    MONOTONIC_GROWTH,

    /**
     * Growth rate (items per second) exceeded the configured threshold.
     */
    GROWTH_VELOCITY_EXCEEDED,

    /**
     * Current collection/map size exceeded the absolute safe capacity threshold.
     */
    CAPACITY_LIMIT_EXCEEDED
}
