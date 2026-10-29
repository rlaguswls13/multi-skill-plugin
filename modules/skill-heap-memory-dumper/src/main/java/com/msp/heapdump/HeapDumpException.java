package com.msp.heapdump;

/**
 * Exception thrown when a heap memory dump operation fails or diagnostic MXBean is unavailable.
 */
public class HeapDumpException extends RuntimeException {

    public HeapDumpException(String message) {
        super(message);
    }

    public HeapDumpException(String message, Throwable cause) {
        super(message, cause);
    }
}
