package com.msp.circuit;

/**
 * States of the Circuit Breaker pattern.
 */
public enum CircuitState {
    /**
     * Normal operation. Requests are allowed through and monitored.
     */
    CLOSED,

    /**
     * Tripped state. Requests are immediately rejected without reaching the downstream service.
     */
    OPEN,

    /**
     * Probe trial state after the wait duration elapses.
     * A limited number of trial calls are allowed to evaluate if the downstream service has recovered.
     */
    HALF_OPEN
}
