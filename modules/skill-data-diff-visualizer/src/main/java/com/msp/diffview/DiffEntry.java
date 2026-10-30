package com.msp.diffview;

import java.util.Objects;

/**
 * Represents a specific key diff between before and after states.
 *
 * @param path     the key path (e.g., "name" or "user.address.city")
 * @param type     diff classification
 * @param oldValue previous value (null if ADDED)
 * @param newValue current value (null if REMOVED)
 */
public record DiffEntry(
        String path,
        DiffType type,
        Object oldValue,
        Object newValue
) {
    public DiffEntry {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    public boolean hasChange() {
        return type != DiffType.UNCHANGED;
    }
}
