package com.msp.cose;

/**
 * Exception thrown when CBOR decoding fails.
 */
public class CborParseException extends RuntimeException {
    public CborParseException(String message) {
        super(message);
    }

    public CborParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
