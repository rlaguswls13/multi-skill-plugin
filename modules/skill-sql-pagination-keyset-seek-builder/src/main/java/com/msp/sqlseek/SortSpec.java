package com.msp.sqlseek;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Specification of a single sort column in keyset pagination.
 *
 * @param column    The SQL column or qualified identifier (e.g., "created_at" or "u.id")
 * @param direction The sort direction (ASC or DESC)
 */
public record SortSpec(String column, SortDirection direction) {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");

    public SortSpec {
        Objects.requireNonNull(column, "column must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        String trimmed = column.trim();
        if (trimmed.isEmpty() || !SAFE_IDENTIFIER.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid or unsafe SQL column identifier: '" + column + "'");
        }
        column = trimmed;
    }

    public static SortSpec asc(String column) {
        return new SortSpec(column, SortDirection.ASC);
    }

    public static SortSpec desc(String column) {
        return new SortSpec(column, SortDirection.DESC);
    }

    public SortSpec invertDirection() {
        return new SortSpec(column, direction.invert());
    }
}
