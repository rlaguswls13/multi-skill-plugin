package com.msp.linkheader;

/**
 * Exception thrown when parsing or formatting an RFC 8288 HTTP Link header fails.
 */
public final class LinkHeaderException extends RuntimeException {

    private final int position;

    public LinkHeaderException(String message) {
        super(message);
        this.position = -1;
    }

    public LinkHeaderException(String message, int position) {
        super(message + " (at index " + position + ")");
        this.position = position;
    }

    /**
     * Zero-based character index where the parse error occurred, or {@code -1} if not applicable.
     */
    public int position() {
        return position;
    }
}
