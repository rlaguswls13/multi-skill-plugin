package com.msp.concurrencygate;

/**
 * Base exception for concurrency gate rejections and failures.
 */
public class GateException extends RuntimeException {
    public GateException(String message) {
        super(message);
    }

    public GateException(String message, Throwable cause) {
        super(message, cause);
    }
}
