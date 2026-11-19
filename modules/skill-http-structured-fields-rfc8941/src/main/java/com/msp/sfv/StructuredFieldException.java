package com.msp.sfv;

/**
 * Exception thrown when parsing or serializing an RFC 8941 / RFC 9651 HTTP Structured Field Value fails.
 */
public final class StructuredFieldException extends RuntimeException {
    private final int position;

    public StructuredFieldException(String message) {
        super(message);
        this.position = -1;
    }

    public StructuredFieldException(String message, int position) {
        super(message + " (at offset " + position + ")");
        this.position = position;
    }

    public int getPosition() {
        return position;
    }
}
