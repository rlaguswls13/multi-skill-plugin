package com.msp.jsonpatch;

/**
 * Thrown when an RFC 6902 {@code test} operation fails due to value mismatch.
 */
public class JsonPatchTestFailedException extends JsonPatchException {

    private final String path;
    private final Object expected;
    private final Object actual;

    public JsonPatchTestFailedException(String path, Object expected, Object actual) {
        super("JSON Patch 'test' failed at path '" + path + "': expected=" + expected + ", actual=" + actual);
        this.path = path;
        this.expected = expected;
        this.actual = actual;
    }

    public String getPath() {
        return path;
    }

    public Object getExpected() {
        return expected;
    }

    public Object getActual() {
        return actual;
    }
}
