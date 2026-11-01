package com.msp.jsonpatchgen;

/**
 * Exception thrown when a JSON string is malformed or invalid according to RFC 8259.
 */
public class JsonParseException extends RuntimeException {

    public JsonParseException(String message) {
        super(message);
    }

    public JsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
