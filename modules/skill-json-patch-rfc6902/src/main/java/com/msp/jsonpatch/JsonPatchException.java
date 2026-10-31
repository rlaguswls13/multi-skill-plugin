package com.msp.jsonpatch;

/**
 * General runtime exception thrown when JSON Patch processing fails.
 */
public class JsonPatchException extends RuntimeException {

    public JsonPatchException(String message) {
        super(message);
    }

    public JsonPatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
