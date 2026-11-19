package com.msp.hpack;

/**
 * Exception thrown when an HPACK compression or decompression protocol error occurs.
 */
public class HpackException extends RuntimeException {

    public HpackException(String message) {
        super(message);
    }

    public HpackException(String message, Throwable cause) {
        super(message, cause);
    }
}
