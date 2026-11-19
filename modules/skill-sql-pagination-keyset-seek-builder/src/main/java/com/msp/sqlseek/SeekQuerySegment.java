package com.msp.sqlseek;

import java.util.List;
import java.util.Objects;

/**
 * Immutable output segment containing generated SQL clauses and bind parameters for keyset seek pagination.
 *
 * @param wherePredicate The WHERE condition predicate (empty string if initial first page with no cursor values)
 * @param orderByClause  The ORDER BY clause (e.g. {@code "ORDER BY created_at DESC, priority ASC, id DESC"})
 * @param limitClause    The LIMIT clause (e.g. {@code "LIMIT 25"})
 * @param bindParameters The ordered positional parameters corresponding to {@code ?} placeholders in {@code wherePredicate}
 */
public record SeekQuerySegment(
        String wherePredicate,
        String orderByClause,
        String limitClause,
        List<Object> bindParameters
) {
    public SeekQuerySegment {
        Objects.requireNonNull(wherePredicate, "wherePredicate must not be null");
        Objects.requireNonNull(orderByClause, "orderByClause must not be null");
        Objects.requireNonNull(limitClause, "limitClause must not be null");
        Objects.requireNonNull(bindParameters, "bindParameters must not be null");
        bindParameters = List.copyOf(bindParameters);
    }

    /**
     * Returns true if a keyset WHERE predicate is present (i.e. cursor values were provided).
     */
    public boolean hasWherePredicate() {
        return !wherePredicate.isEmpty();
    }

    /**
     * Formats a complete SQL query suffix or combines with a base SELECT query.
     * If the base query already ends with WHERE or contains no WHERE, callers can use {@link #toSqlSuffix()}.
     */
    public String toSqlSuffix() {
        StringBuilder sb = new StringBuilder();
        if (hasWherePredicate()) {
            sb.append("WHERE ").append(wherePredicate).append(' ');
        }
        sb.append(orderByClause).append(' ').append(limitClause);
        return sb.toString();
    }
}
