package com.msp.diff;

import java.util.Objects;

/**
 * An immutable record representing a single change detected in a nested object structure.
 *
 * @param changeType type of change (ADDED, REMOVED, MODIFIED)
 * @param path       dot/bracket notation path (e.g. "address.city", "items[0].price")
 * @param oldValue   value before change (null if ADDED)
 * @param newValue   value after change (null if REMOVED)
 */
public record FieldChange(
        ChangeType changeType,
        String path,
        Object oldValue,
        Object newValue
) {

    public FieldChange {
        Objects.requireNonNull(changeType, "changeType must not be null");
        Objects.requireNonNull(path, "path must not be null");
    }

    /**
     * Formats this change into a standard human-readable audit log string.
     * Example: "[address.city] 'Seoul' -> 'Busan'"
     * Example: "[hobbies[2]] ADDED: 'Gaming'"
     * Example: "[phone] REMOVED: '010-1234-5678'"
     */
    public String toAuditString() {
        return switch (changeType) {
            case ADDED -> String.format("[%s] ADDED: '%s'", path, formatValue(newValue));
            case REMOVED -> String.format("[%s] REMOVED: '%s'", path, formatValue(oldValue));
            case MODIFIED -> String.format("[%s] '%s' -> '%s'", path, formatValue(oldValue), formatValue(newValue));
        };
    }

    private static String formatValue(Object val) {
        return (val == null) ? "null" : val.toString();
    }
}
