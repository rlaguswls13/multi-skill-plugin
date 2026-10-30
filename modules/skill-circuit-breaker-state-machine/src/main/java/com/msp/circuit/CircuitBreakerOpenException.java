package com.msp.circuit;

/**
 * Thrown when an execution is attempted on a CircuitBreaker that is OPEN
 * or has exhausted permits in HALF_OPEN state.
 */
public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}
