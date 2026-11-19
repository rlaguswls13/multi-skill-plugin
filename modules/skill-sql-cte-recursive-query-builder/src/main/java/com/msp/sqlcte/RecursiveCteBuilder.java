package com.msp.sqlcte;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for recursive Common Table Expressions.
 */
public final class RecursiveCteBuilder {

    private final CteBuilder parentBuilder;
    private final String name;
    private final List<String> columnAliases = new ArrayList<>();
    private String anchorSql;
    private UnionType unionType = UnionType.UNION_ALL;
    private String recursiveSql;
    private CycleDetectionConfig cycleDetection = CycleDetectionConfig.none();

    RecursiveCteBuilder(String name) {
        this(null, name);
    }

    RecursiveCteBuilder(CteBuilder parentBuilder, String name) {
        this.parentBuilder = parentBuilder;
        this.name = Objects.requireNonNull(name, "CTE name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("CTE name cannot be blank");
        }
    }

    public RecursiveCteBuilder columns(String... columns) {
        if (columns != null) {
            this.columnAliases.addAll(Arrays.asList(columns));
        }
        return this;
    }

    public RecursiveCteBuilder columns(List<String> columns) {
        if (columns != null) {
            this.columnAliases.addAll(columns);
        }
        return this;
    }

    public RecursiveCteBuilder anchorSelect(String anchorSql) {
        this.anchorSql = Objects.requireNonNull(anchorSql, "Anchor SQL cannot be null");
        return this;
    }

    public RecursiveCteBuilder unionAll() {
        this.unionType = UnionType.UNION_ALL;
        return this;
    }

    public RecursiveCteBuilder union() {
        this.unionType = UnionType.UNION;
        return this;
    }

    public RecursiveCteBuilder union(UnionType type) {
        this.unionType = Objects.requireNonNull(type, "UnionType cannot be null");
        return this;
    }

    public RecursiveCteBuilder recursiveSelect(String recursiveSql) {
        this.recursiveSql = Objects.requireNonNull(recursiveSql, "Recursive SQL cannot be null");
        return this;
    }

    public RecursiveCteBuilder cycleDetection(CycleDetectionConfig config) {
        this.cycleDetection = Objects.requireNonNull(config, "CycleDetectionConfig cannot be null");
        return this;
    }

    public RecursiveCteBuilder ansiCycle(String cycleColumn, String flagColumn, String pathColumn) {
        return cycleDetection(CycleDetectionConfig.ansiCycle(cycleColumn, flagColumn, pathColumn));
    }

    public RecursiveCteBuilder detectCycleByPath(String pathColumn, String idColumn) {
        return cycleDetection(CycleDetectionConfig.pathContains(pathColumn, idColumn));
    }

    public RecursiveCteBuilder maxDepth(String depthColumn, int maxDepth) {
        return cycleDetection(CycleDetectionConfig.maxDepth(depthColumn, maxDepth));
    }

    public RecursiveCteDefinition buildDefinition() {
        if (anchorSql == null || anchorSql.isBlank()) {
            throw new IllegalStateException("Anchor query must be specified");
        }
        if (recursiveSql == null || recursiveSql.isBlank()) {
            throw new IllegalStateException("Recursive query must be specified");
        }
        return new RecursiveCteDefinition(name, columnAliases, anchorSql, unionType, recursiveSql, cycleDetection);
    }

    /**
     * Completes this recursive CTE and returns the parent {@link CteBuilder} for chaining further CTEs.
     */
    public CteBuilder and() {
        CteBuilder builder = (parentBuilder != null) ? parentBuilder : CteBuilder.create();
        builder.withRecursive(buildDefinition());
        return builder;
    }

    /**
     * Convenience terminal operation: finishes this CTE and builds a complete {@link CteQuery}
     * with the given final SELECT statement.
     */
    public CteQuery finalSelect(String finalSelectSql) {
        CteBuilder builder = (parentBuilder != null) ? parentBuilder : CteBuilder.create();
        builder.withRecursive(buildDefinition());
        return builder.finalSelect(finalSelectSql);
    }
}
