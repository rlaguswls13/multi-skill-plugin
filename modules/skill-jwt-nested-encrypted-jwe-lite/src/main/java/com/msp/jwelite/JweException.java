package com.msp.jwelite;

/**
 * Exception thrown when JWE encryption, decryption, or parsing fails.
 */
public class JweException extends RuntimeException {

    public JweException(String message) {
        super(message);
    }

    public JweException(String message, Throwable cause) {
        super(message, cause);
    }
}
