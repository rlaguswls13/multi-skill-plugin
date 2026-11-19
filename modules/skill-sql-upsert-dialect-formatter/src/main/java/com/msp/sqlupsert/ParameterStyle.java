package com.msp.sqlupsert;

/**
 * Parameter placeholder styles for generated SQL.
 */
public enum ParameterStyle {
    /**
     * Standard JDBC positional placeholder: `?`
     */
    POSITIONAL,

    /**
     * Named placeholder with colon prefix: `:columnName`
     */
    NAMED_COLON,

    /**
     * Named placeholder with dollar prefix: `${columnName}`
     */
    NAMED_DOLLAR;

    /**
     * Formats placeholder token for a given column name.
     */
    public String placeholder(String column) {
        return switch (this) {
            case POSITIONAL -> "?";
            case NAMED_COLON -> ":" + column;
            case NAMED_DOLLAR -> "${" + column + "}";
        };
    }
}
