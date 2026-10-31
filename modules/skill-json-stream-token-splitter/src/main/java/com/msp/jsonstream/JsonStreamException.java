package com.msp.jsonstream;

/**
 * Exception thrown when stream token splitting encounters an unrecoverable format or I/O error.
 */
public class JsonStreamException extends RuntimeException {

    public JsonStreamException(String message) {
        super(message);
    }

    public JsonStreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
