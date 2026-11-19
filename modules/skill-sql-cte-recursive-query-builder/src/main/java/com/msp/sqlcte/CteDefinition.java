package com.msp.sqlcte;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Standard (non-recursive) Common Table Expression definition.
 */
public record CteDefinition(
        String name,
        List<String> columnAliases,
        String query
) {
    public CteDefinition {
        Objects.requireNonNull(name, "CTE name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("CTE name cannot be blank");
        }
        Objects.requireNonNull(query, "CTE query cannot be null");
        if (query.isBlank()) {
            throw new IllegalArgumentException("CTE query cannot be blank");
        }
        columnAliases = columnAliases == null ? List.of() : List.copyOf(columnAliases);
    }

    public CteDefinition(String name, String query) {
        this(name, List.of(), query);
    }

    public String toSql() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (!columnAliases.isEmpty()) {
            sb.append(" (").append(String.join(", ", columnAliases)).append(")");
        }
        sb.append(" AS (\n  ").append(query.strip().replace("\n", "\n  ")).append("\n)");
        return sb.toString();
    }
}
