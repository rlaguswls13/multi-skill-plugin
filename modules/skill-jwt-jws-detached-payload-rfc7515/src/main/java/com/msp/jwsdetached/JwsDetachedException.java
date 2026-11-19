package com.msp.jwsdetached;

/**
 * Exception thrown when JWS detached signing, parsing, or verification fails.
 */
public final class JwsDetachedException extends RuntimeException {
    public JwsDetachedException(String message) {
        super(message);
    }

    public JwsDetachedException(String message, Throwable cause) {
        super(message, cause);
    }
}
