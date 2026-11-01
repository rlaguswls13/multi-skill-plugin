package com.msp.sqlsanitizer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and sanitizes SQL identifiers (table, column, schema, alias names)
 * to prevent SQL Injection in dynamic query construction.
 */
public final class SqlIdentifierSanitizer {

    public static final int MAX_IDENTIFIER_LENGTH = 128;

    private static final Pattern VALID_IDENTIFIER_PATTERN =
            Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private static final Pattern ILLEGAL_CHARS_PATTERN =
            Pattern.compile("[;=<>\\(\\)\\[\\]\\{\\}\\\\']");

    private static final Set<String> RESERVED_KEYWORDS;

    static {
        Set<String> keywords = new HashSet<>();
        String[] words = {
                "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "BETWEEN", "BY", "CASE", "CHECK",
                "COLUMN", "CONSTRAINT", "CREATE", "DATABASE", "DEFAULT", "DELETE", "DESC",
                "DISTINCT", "DROP", "ELSE", "END", "EXISTS", "FOREIGN", "FROM", "FULL",
                "GROUP", "HAVING", "IN", "INDEX", "INNER", "INSERT", "INTO", "IS", "JOIN",
                "KEY", "LEFT", "LIKE", "LIMIT", "NOT", "NULL", "OFFSET", "ON", "OR", "ORDER",
                "OUTER", "PRIMARY", "REFERENCES", "RIGHT", "SELECT", "SET", "TABLE", "THEN",
                "UNION", "UNIQUE", "UPDATE", "USER", "VALUES", "VIEW", "WHEN", "WHERE", "WITH"
        };
        Collections.addAll(keywords, words);
        RESERVED_KEYWORDS = Collections.unmodifiableSet(keywords);
    }

    private SqlIdentifierSanitizer() {
        // Utility class
    }

    /**
     * Checks if the given identifier is a valid, unquoted SQL identifier.
     * Must be alphanumeric with underscores, starts with a letter or underscore,
     * within length limits, and free of injection characters or comment sequences.
     *
     * @param identifier candidate identifier
     * @return true if valid, false otherwise
     */
    public static boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty() || identifier.length() > MAX_IDENTIFIER_LENGTH) {
            return false;
        }
        if (identifier.contains("\0") || identifier.contains(";") ||
                identifier.contains("--") || identifier.contains("/*") || identifier.contains("*/")) {
            return false;
        }
        return VALID_IDENTIFIER_PATTERN.matcher(identifier).matches();
    }

    /**
     * Checks if a word is a standard SQL reserved keyword (case-insensitive).
     *
     * @param word word to check
     * @return true if reserved
     */
    public static boolean isReservedKeyword(String word) {
        if (word == null || word.isBlank()) {
            return false;
        }
        return RESERVED_KEYWORDS.contains(word.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Sanitizes and quotes a single SQL identifier for the specified dialect.
     * Internal dialect quote characters are escaped (e.g., " -> "" or ` -> ``).
     *
     * @param identifier identifier to sanitize
     * @param dialect    target SQL dialect
     * @return safely quoted identifier
     * @throws IllegalArgumentException if identifier is null, blank, exceeds max length,
     *                                  or contains injection patterns
     */
    public static String sanitize(String identifier, SqlDialect dialect) {
        Objects.requireNonNull(dialect, "dialect must not be null");
        validateInjectionSafeness(identifier, false);
        return dialect.quote(identifier.trim());
    }

    /**
     * Sanitizes and quotes a qualified identifier (e.g., "schema.table.column")
     * by sanitizing each segment individually and joining them with a dot.
     *
     * @param qualifiedName qualified identifier path
     * @param dialect       target SQL dialect
     * @return safely quoted qualified identifier
     * @throws IllegalArgumentException if qualifiedName is null, blank, or malformed
     */
    public static String sanitizeQualified(String qualifiedName, SqlDialect dialect) {
        Objects.requireNonNull(dialect, "dialect must not be null");
        if (qualifiedName == null || qualifiedName.isBlank()) {
            throw new IllegalArgumentException("Qualified identifier must not be null or blank");
        }

        String trimmed = qualifiedName.trim();
        if (trimmed.startsWith(".") || trimmed.endsWith(".") || trimmed.contains("..")) {
            throw new IllegalArgumentException("Malformed qualified identifier: " + qualifiedName);
        }

        String[] parts = trimmed.split("\\.", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(sanitize(parts[i], dialect));
        }
        return sb.toString();
    }

    private static void validateInjectionSafeness(String identifier, boolean allowDots) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier must not be null or blank");
        }

        if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException("Identifier exceeds maximum length of " + MAX_IDENTIFIER_LENGTH + " characters: " + identifier.length());
        }

        if (identifier.contains("\0")) {
            throw new IllegalArgumentException("Identifier contains null byte (SQL Injection attempt detected)");
        }

        if (identifier.contains(";")) {
            throw new IllegalArgumentException("Identifier contains semicolon (SQL Injection attempt detected)");
        }

        if (identifier.contains("--") || identifier.contains("/*") || identifier.contains("*/")) {
            throw new IllegalArgumentException("Identifier contains SQL comment sequence (SQL Injection attempt detected)");
        }

        if (identifier.contains("'")) {
            throw new IllegalArgumentException("Identifier contains single quote (SQL Injection attempt detected)");
        }

        for (int i = 0; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (Character.isISOControl(c)) {
                throw new IllegalArgumentException("Identifier contains control characters: index " + i);
            }
        }

        if (ILLEGAL_CHARS_PATTERN.matcher(identifier).find()) {
            throw new IllegalArgumentException("Identifier contains forbidden injection characters: " + identifier);
        }

        if (!allowDots && identifier.contains(".")) {
            throw new IllegalArgumentException("Unqualified identifier cannot contain dots. Use sanitizeQualified() instead.");
        }
    }
}
