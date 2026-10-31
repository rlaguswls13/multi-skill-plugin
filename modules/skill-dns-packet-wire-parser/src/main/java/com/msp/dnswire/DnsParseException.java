package com.msp.dnswire;

/**
 * Exception thrown when parsing malformed or corrupted DNS wire-format bytes.
 */
public class DnsParseException extends RuntimeException {

    public DnsParseException(String message) {
        super(message);
    }

    public DnsParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
