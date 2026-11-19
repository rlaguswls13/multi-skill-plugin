package com.msp.digestauth;

/**
 * Runtime exception thrown when HTTP Digest Authentication headers or parameters are malformed.
 */
public final class DigestAuthException extends RuntimeException {
    public DigestAuthException(String message) {
        super(message);
    }

    public DigestAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
