package com.msp.jsonpatchgen;

/**
 * Supported RFC 6902 JSON Patch operations.
 */
public enum JsonPatchOp {
    ADD("add"),
    REMOVE("remove"),
    REPLACE("replace");

    private final String opName;

    JsonPatchOp(String opName) {
        this.opName = opName;
    }

    public String opName() {
        return opName;
    }
}
