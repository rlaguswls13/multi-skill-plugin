package com.msp.bencode;

/**
 * Exception thrown when Bencode parsing or encoding encounters a malformed structure.
 */
public class BencodeException extends RuntimeException {

    public BencodeException(String message) {
        super(message);
    }

    public BencodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
