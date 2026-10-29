package com.msp.resilience;

/**
 * Exception thrown when a call is rejected by an OPEN CircuitBreaker.
 */
public class CircuitBreakerOpenException extends RuntimeException {

    private final String circuitBreakerName;

    public CircuitBreakerOpenException(String circuitBreakerName) {
        super("CircuitBreaker '" + circuitBreakerName + "' is OPEN and refusing calls");
        this.circuitBreakerName = circuitBreakerName;
    }

    public String circuitBreakerName() {
        return circuitBreakerName;
    }
}
