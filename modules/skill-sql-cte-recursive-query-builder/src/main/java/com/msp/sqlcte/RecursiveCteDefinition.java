package com.msp.sqlcte;

import java.util.List;
import java.util.Objects;

/**
 * Recursive Common Table Expression definition.
 */
public record RecursiveCteDefinition(
        String name,
        List<String> columnAliases,
        String anchorSql,
        UnionType unionType,
        String recursiveSql,
        CycleDetectionConfig cycleDetection
) {
    public RecursiveCteDefinition {
        Objects.requireNonNull(name, "CTE name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("CTE name cannot be blank");
        }
        Objects.requireNonNull(anchorSql, "Anchor query cannot be null");
        if (anchorSql.isBlank()) {
            throw new IllegalArgumentException("Anchor query cannot be blank");
        }
        Objects.requireNonNull(recursiveSql, "Recursive query cannot be null");
        if (recursiveSql.isBlank()) {
            throw new IllegalArgumentException("Recursive query cannot be blank");
        }
        unionType = unionType == null ? UnionType.UNION_ALL : unionType;
        columnAliases = columnAliases == null ? List.of() : List.copyOf(columnAliases);
        cycleDetection = cycleDetection == null ? CycleDetectionConfig.none() : cycleDetection;
    }

    public String toSql() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (!columnAliases.isEmpty()) {
            sb.append(" (").append(String.join(", ", columnAliases)).append(")");
        }
        sb.append(" AS (\n");
        sb.append("  ").append(anchorSql.strip().replace("\n", "\n  ")).append("\n");
        sb.append("  ").append(unionType.keyword()).append("\n");

        String effectiveRecursive = recursiveSql.strip();
        if (cycleDetection instanceof CycleDetectionConfig.PathContainment pc) {
            effectiveRecursive = appendWhereCondition(effectiveRecursive, pc.toWhereClause());
        } else if (cycleDetection instanceof CycleDetectionConfig.MaxDepth md) {
            effectiveRecursive = appendWhereCondition(effectiveRecursive, md.toWhereClause());
        }

        sb.append("  ").append(effectiveRecursive.replace("\n", "\n  ")).append("\n");
        sb.append(")");

        if (cycleDetection instanceof CycleDetectionConfig.AnsiCycleClause ac) {
            sb.append("\n").append(ac.toSqlClause());
        }

        return sb.toString();
    }

    private static String appendWhereCondition(String sql, String condition) {
        String upper = sql.toUpperCase();
        int whereIndex = upper.lastIndexOf("WHERE");
        if (whereIndex >= 0) {
            // Check if there is GROUP BY, ORDER BY, or LIMIT after WHERE
            int groupIndex = upper.indexOf("GROUP BY", whereIndex);
            int orderIndex = upper.indexOf("ORDER BY", whereIndex);
            int limitIndex = upper.indexOf("LIMIT", whereIndex);
            int insertIndex = sql.length();
            if (groupIndex >= 0) insertIndex = Math.min(insertIndex, groupIndex);
            if (orderIndex >= 0) insertIndex = Math.min(insertIndex, orderIndex);
            if (limitIndex >= 0) insertIndex = Math.min(insertIndex, limitIndex);

            String before = sql.substring(0, insertIndex).stripTrailing();
            String after = sql.substring(insertIndex);
            return before + " AND (" + condition + ")" + (after.isEmpty() ? "" : "\n" + after);
        } else {
            // Check if there is GROUP BY, ORDER BY, LIMIT
            int groupIndex = upper.lastIndexOf("GROUP BY");
            int orderIndex = upper.lastIndexOf("ORDER BY");
            int limitIndex = upper.lastIndexOf("LIMIT");
            int insertIndex = sql.length();
            if (groupIndex >= 0) insertIndex = Math.min(insertIndex, groupIndex);
            if (orderIndex >= 0) insertIndex = Math.min(insertIndex, orderIndex);
            if (limitIndex >= 0) insertIndex = Math.min(insertIndex, limitIndex);

            String before = sql.substring(0, insertIndex).stripTrailing();
            String after = sql.substring(insertIndex);
            return before + "\nWHERE " + condition + (after.isEmpty() ? "" : "\n" + after);
        }
    }
}
