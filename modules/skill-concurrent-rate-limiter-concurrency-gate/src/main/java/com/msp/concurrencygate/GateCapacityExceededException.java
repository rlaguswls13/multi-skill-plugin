package com.msp.concurrencygate;

/**
 * Thrown when a task is submitted or enqueued, but the queue capacity has been reached.
 */
public class GateCapacityExceededException extends GateException {
    public GateCapacityExceededException(String message) {
        super(message);
    }
}
