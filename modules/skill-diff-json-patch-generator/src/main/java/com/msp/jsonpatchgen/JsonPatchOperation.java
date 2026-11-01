package com.msp.jsonpatchgen;

import java.util.Objects;

/**
 * An RFC 6902 JSON Patch operation.
 *
 * @param op    the patch operation type (ADD, REMOVE, REPLACE)
 * @param path  the RFC 6901 JSON Pointer target path (e.g. "/a/b/0")
 * @param value the value associated with the operation (always null for REMOVE)
 */
public record JsonPatchOperation(JsonPatchOp op, String path, Object value) {

    public JsonPatchOperation {
        Objects.requireNonNull(op, "op must not be null");
        Objects.requireNonNull(path, "path must not be null");
        if (op == JsonPatchOp.REMOVE) {
            value = null;
        }
    }
}
