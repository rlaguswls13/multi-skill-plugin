package com.msp.paseto;

/**
 * Exception thrown when PASETO v3.local token encryption, parsing, or authentication fails.
 */
public final class PasetoException extends RuntimeException {

    public PasetoException(String message) {
        super(message);
    }

    public PasetoException(String message, Throwable cause) {
        super(message, cause);
    }
}
