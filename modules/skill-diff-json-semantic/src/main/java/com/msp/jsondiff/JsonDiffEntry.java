package com.msp.jsondiff;

import java.util.Objects;

/**
 * Immutable record representing a single semantic diff entry.
 *
 * @param path     dot-notation path of the property (e.g., "user.address.city", "items[0].name")
 * @param type     diff type (ADDED, REMOVED, MODIFIED, UNCHANGED)
 * @param oldValue previous value (null if ADDED)
 * @param newValue current value (null if REMOVED)
 */
public record JsonDiffEntry(
        String path,
        DiffType type,
        Object oldValue,
        Object newValue
) {
    public JsonDiffEntry {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }
}
