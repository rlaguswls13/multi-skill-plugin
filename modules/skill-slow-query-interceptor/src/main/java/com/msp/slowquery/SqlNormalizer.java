package com.msp.slowquery;

import java.util.regex.Pattern;

/**
 * Utility for normalizing raw SQL queries into parameterized templates by replacing
 * string literals, numeric values, and collapsing repeated list elements.
 */
public final class SqlNormalizer {

    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("'(?:[^'\\\\]|\\\\.|'')*'");
    // Matches standalone numbers (integer, floating point) bounded by whitespace, operators, commas, parentheses
    private static final Pattern NUMERIC_LITERAL_PATTERN = Pattern.compile("(?<=[^a-zA-Z0-9_#$]|^)[+-]?\\d+(?:\\.\\d+)?(?=[^a-zA-Z0-9_#$]|$)");
    // Collapses IN (?, ?, ?) to IN (?)
    private static final Pattern IN_LIST_PATTERN = Pattern.compile("(?i)\\bIN\\s*\\(\\s*\\?(?:\\s*,\\s*\\?)+\\s*\\)");
    // Collapses multiple consecutive whitespaces, tabs, newlines
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private SqlNormalizer() {
    }

    /**
     * Normalizes a raw SQL query string.
     *
     * @param rawSql the original SQL query
     * @return normalized parameterized SQL template
     */
    public static String normalize(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            return "";
        }

        // 1. Replace string literals with ?
        String result = STRING_LITERAL_PATTERN.matcher(rawSql).replaceAll("?");

        // 2. Replace numeric literals with ?
        result = NUMERIC_LITERAL_PATTERN.matcher(result).replaceAll("?");

        // 3. Normalize IN (?, ?, ...) clauses to IN (?)
        result = IN_LIST_PATTERN.matcher(result).replaceAll("IN (?)");

        // 4. Collapse consecutive whitespaces and trim
        result = WHITESPACE_PATTERN.matcher(result).replaceAll(" ").trim();

        return result;
    }
}
