package com.msp.auditlog;

import java.util.Objects;

/**
 * Represents a before-and-after difference for a specific field.
 *
 * @param field    the name of the changed field
 * @param oldValue the previous value (nullable)
 * @param newValue the updated value (nullable)
 */
public record FieldDiff(
        String field,
        Object oldValue,
        Object newValue
) {
    public FieldDiff {
        Objects.requireNonNull(field, "field name must not be null");
    }

    public static FieldDiff of(String field, Object oldValue, Object newValue) {
        return new FieldDiff(field, oldValue, newValue);
    }
}
