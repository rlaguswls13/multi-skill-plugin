package com.msp.concurrencygate;

/**
 * Thrown when waiting in the concurrency gate's queue exceeds the configured timeout.
 */
public class GateTimeoutException extends GateException {
    public GateTimeoutException(String message) {
        super(message);
    }
}
