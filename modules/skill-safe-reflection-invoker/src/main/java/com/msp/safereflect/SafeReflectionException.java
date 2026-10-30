package com.msp.safereflect;

/**
 * Unchecked runtime exception wrapping Java reflection errors.
 */
public class SafeReflectionException extends RuntimeException {

    public SafeReflectionException(String message) {
        super(message);
    }

    public SafeReflectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
