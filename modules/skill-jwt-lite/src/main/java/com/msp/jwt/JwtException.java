package com.msp.jwt;

/**
 * Base unchecked exception for all JWT parsing, validation, and signing failures.
 */
public class JwtException extends RuntimeException {

    public JwtException(String message) {
        super(message);
    }

    public JwtException(String message, Throwable cause) {
        super(message, cause);
    }
}
