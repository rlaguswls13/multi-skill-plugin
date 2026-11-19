package com.msp.sqljson;

/**
 * Supported SQL dialects for JSON path extraction and predicate generation.
 */
public enum SqlJsonDialect {
    /** PostgreSQL JSONB operators ({@code ->}, {@code ->>}, {@code #>}, {@code #>>}, {@code @>}, {@code ?}). */
    POSTGRESQL,
    /** MySQL JSON functions ({@code JSON_EXTRACT}, {@code JSON_UNQUOTE}, {@code JSON_CONTAINS}, {@code JSON_CONTAINS_PATH}). */
    MYSQL,
    /** SQLite JSON1 extension functions ({@code json_extract}, {@code json_type}). */
    SQLITE
}
