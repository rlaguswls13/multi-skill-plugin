package com.msp.sqllexer;

/**
 * Represents a token produced by the SQL lexer.
 *
 * @param type     token category
 * @param value    exact string representation of the token in SQL text
 * @param position 0-indexed character offset where this token starts
 */
public record SqlToken(SqlTokenType type, String value, int position) {

    public SqlToken {
        if (type == null) {
            throw new IllegalArgumentException("SqlTokenType cannot be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Token value cannot be null");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative: " + position);
        }
    }

    /**
     * Case-insensitive check if this token matches a specific keyword or value.
     */
    public boolean matchesIgnoreCase(String text) {
        return value.equalsIgnoreCase(text);
    }
}
