package com.msp.clausesplit;

import java.util.Objects;

/**
 * Represents an individual SQL clause comprising its introductory keyword,
 * body content, and token bounds within the source SQL query.
 *
 * @param type       The recognized {@link ClauseType}
 * @param keyword    The actual keyword as parsed from the original SQL (preserving case)
 * @param body       The clause body text between this keyword and the subsequent clause
 * @param startIndex The starting character index in the source SQL statement
 * @param endIndex   The ending character index (exclusive) in the source SQL statement
 */
public record SqlClause(
    ClauseType type,
    String keyword,
    String body,
    int startIndex,
    int endIndex
) {
    public SqlClause {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(keyword, "keyword cannot be null");
        Objects.requireNonNull(body, "body cannot be null");
    }

    /**
     * Returns the clause body stripped of leading and trailing whitespace.
     */
    public String trimmedBody() {
        return body.strip();
    }

    /**
     * Formats this clause as "KEYWORD body".
     */
    public String toSql() {
        String cleanBody = trimmedBody();
        if (cleanBody.isEmpty()) {
            return keyword;
        }
        return keyword + " " + cleanBody;
    }
}
