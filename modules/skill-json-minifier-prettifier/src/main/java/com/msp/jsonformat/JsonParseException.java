package com.msp.jsonformat;

/**
 * Exception thrown when JSON formatting or minifying encounters invalid or unbalanced JSON.
 */
public class JsonParseException extends RuntimeException {
    public JsonParseException(String message) {
        super(message);
    }

    public JsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
