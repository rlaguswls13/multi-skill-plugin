package com.msp.fixedlen;

/**
 * Exception thrown when fixed-length packing or unpacking fails.
 */
public class FixedLengthException extends RuntimeException {
    public FixedLengthException(String message) {
        super(message);
    }

    public FixedLengthException(String message, Throwable cause) {
        super(message, cause);
    }
}
