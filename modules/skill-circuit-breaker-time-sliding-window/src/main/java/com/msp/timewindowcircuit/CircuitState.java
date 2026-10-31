package com.msp.timewindowcircuit;

/**
 * State representation for TimeWindowCircuitBreaker.
 */
public enum CircuitState {
    /**
     * Normal operation. Requests are allowed through.
     */
    CLOSED,

    /**
     * Tripped state due to excessive failure rate. Requests are rejected immediately.
     */
    OPEN,

    /**
     * Trial recovery state. A limited number of probe requests are permitted to test downstream health.
     */
    HALF_OPEN
}
