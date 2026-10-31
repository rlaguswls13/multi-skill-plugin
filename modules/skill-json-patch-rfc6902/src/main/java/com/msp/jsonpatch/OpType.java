package com.msp.jsonpatch;

import java.util.Locale;

/**
 * RFC 6902 JSON Patch operation types.
 */
public enum OpType {
    ADD("add"),
    REMOVE("remove"),
    REPLACE("replace"),
    MOVE("move"),
    COPY("copy"),
    TEST("test");

    private final String jsonName;

    OpType(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }

    /**
     * Parses operation name case-insensitively.
     */
    public static OpType from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("OpType cannot be null");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "add" -> ADD;
            case "remove" -> REMOVE;
            case "replace" -> REPLACE;
            case "move" -> MOVE;
            case "copy" -> COPY;
            case "test" -> TEST;
            default -> throw new IllegalArgumentException("Unknown JSON Patch op: " + value);
        };
    }
}
