package com.msp.sqljson;

import java.util.regex.Pattern;

/**
 * Strict validator for SQL column identifiers and JSON path property segments
 * to prevent SQL injection when rendering structural SQL fragments.
 */
public final class SqlIdentifierValidator {

    /**
     * Allows simple or qualified identifiers: {@code col}, {@code tbl.col}, {@code schema.tbl.col}.
     */
    private static final Pattern SQL_IDENTIFIER_PATTERN =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*){0,2}$");

    /**
     * Allows safe JSON object key characters without quotes, spaces, brackets, or SQL metacharacters.
     */
    private static final Pattern JSON_PROPERTY_PATTERN =
            Pattern.compile("^[A-Za-z0-9_$-]+$");

    private SqlIdentifierValidator() {
    }

    /**
     * Validates a SQL column identifier (optionally qualified with table and schema).
     *
     * @param identifier column identifier
     * @return the validated identifier
     * @throws IllegalArgumentException if null, blank, or contains unsafe characters
     */
    public static String validateColumn(String identifier) {
        if (identifier == null || !SQL_IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid SQL column identifier: " + identifier);
        }
        return identifier;
    }

    /**
     * Validates a JSON property key segment.
     *
     * @param property JSON property name
     * @return the validated property name
     * @throws IllegalArgumentException if null, empty, or contains unsafe characters
     */
    public static String validateJsonProperty(String property) {
        if (property == null || !JSON_PROPERTY_PATTERN.matcher(property).matches()) {
            throw new IllegalArgumentException("Invalid JSON path property segment: " + property);
        }
        return property;
    }
}
