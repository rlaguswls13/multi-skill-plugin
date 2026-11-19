package com.msp.sqlcte;

import java.util.List;
import java.util.Objects;

/**
 * Immutable compiled representation of a SQL Common Table Expression (CTE) query.
 */
public record CteQuery(
        List<CteDefinition> standardCtes,
        List<RecursiveCteDefinition> recursiveCtes,
        String finalSelect,
        String sql
) {
    public CteQuery {
        standardCtes = standardCtes == null ? List.of() : List.copyOf(standardCtes);
        recursiveCtes = recursiveCtes == null ? List.of() : List.copyOf(recursiveCtes);
        Objects.requireNonNull(finalSelect, "finalSelect cannot be null");
        Objects.requireNonNull(sql, "sql cannot be null");
    }

    public boolean isRecursive() {
        return !recursiveCtes.isEmpty();
    }

    public String toSql() {
        return sql;
    }

    @Override
    public String toString() {
        return sql;
    }
}
