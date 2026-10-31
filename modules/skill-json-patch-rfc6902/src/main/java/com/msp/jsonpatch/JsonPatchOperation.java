package com.msp.jsonpatch;

/**
 * Represents a single RFC 6902 JSON Patch operation.
 *
 * @param op    the operation type (add, remove, replace, move, copy, test)
 * @param path  the target JSON Pointer path
 * @param value the value to add, replace, or test against (null for remove, move, copy)
 * @param from  the source JSON Pointer path (required for move, copy)
 */
public record JsonPatchOperation(OpType op, String path, Object value, String from) {

    public JsonPatchOperation {
        if (op == null) {
            throw new IllegalArgumentException("op must not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if ((op == OpType.MOVE || op == OpType.COPY) && from == null) {
            throw new IllegalArgumentException("'from' path is required for op=" + op);
        }
    }

    public static JsonPatchOperation add(String path, Object value) {
        return new JsonPatchOperation(OpType.ADD, path, value, null);
    }

    public static JsonPatchOperation remove(String path) {
        return new JsonPatchOperation(OpType.REMOVE, path, null, null);
    }

    public static JsonPatchOperation replace(String path, Object value) {
        return new JsonPatchOperation(OpType.REPLACE, path, value, null);
    }

    public static JsonPatchOperation copy(String from, String path) {
        return new JsonPatchOperation(OpType.COPY, path, null, from);
    }

    public static JsonPatchOperation move(String from, String path) {
        return new JsonPatchOperation(OpType.MOVE, path, null, from);
    }

    public static JsonPatchOperation test(String path, Object value) {
        return new JsonPatchOperation(OpType.TEST, path, value, null);
    }
}
