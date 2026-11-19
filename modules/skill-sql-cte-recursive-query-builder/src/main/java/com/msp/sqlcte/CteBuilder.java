package com.msp.sqlcte;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Fluent query builder for SQL Common Table Expressions (CTE) and WITH RECURSIVE queries.
 */
public final class CteBuilder {

    private final List<CteDefinition> standardCtes = new ArrayList<>();
    private final List<RecursiveCteDefinition> recursiveCtes = new ArrayList<>();

    private CteBuilder() {}

    public static CteBuilder create() {
        return new CteBuilder();
    }

    public static RecursiveCteBuilder recursive(String cteName) {
        return new RecursiveCteBuilder(cteName);
    }

    public static RecursiveCteBuilder recursive(String cteName, String... columnAliases) {
        RecursiveCteBuilder builder = new RecursiveCteBuilder(cteName);
        if (columnAliases != null && columnAliases.length > 0) {
            builder.columns(columnAliases);
        }
        return builder;
    }

    public static CteBuilder with(String cteName, String query) {
        return create().withCte(cteName, query);
    }

    public static CteBuilder with(String cteName, List<String> columns, String query) {
        return create().withCte(cteName, columns, query);
    }

    public CteBuilder withCte(String name, String query) {
        this.standardCtes.add(new CteDefinition(name, List.of(), query));
        return this;
    }

    public CteBuilder withCte(String name, List<String> columns, String query) {
        this.standardCtes.add(new CteDefinition(name, columns, query));
        return this;
    }

    public CteBuilder withCte(CteDefinition cteDefinition) {
        this.standardCtes.add(Objects.requireNonNull(cteDefinition, "cteDefinition cannot be null"));
        return this;
    }

    public CteBuilder withRecursive(RecursiveCteDefinition recursiveCte) {
        this.recursiveCtes.add(Objects.requireNonNull(recursiveCte, "recursiveCte cannot be null"));
        return this;
    }

    public RecursiveCteBuilder recursiveCte(String cteName) {
        return new RecursiveCteBuilder(this, cteName);
    }

    public RecursiveCteBuilder recursiveCte(String cteName, String... columnAliases) {
        RecursiveCteBuilder builder = new RecursiveCteBuilder(this, cteName);
        if (columnAliases != null && columnAliases.length > 0) {
            builder.columns(columnAliases);
        }
        return builder;
    }

    public CteQuery finalSelect(String finalSelectSql) {
        Objects.requireNonNull(finalSelectSql, "Final select query cannot be null");
        if (finalSelectSql.isBlank()) {
            throw new IllegalArgumentException("Final select query cannot be blank");
        }
        if (standardCtes.isEmpty() && recursiveCtes.isEmpty()) {
            throw new IllegalStateException("At least one CTE definition is required");
        }

        StringBuilder sql = new StringBuilder();
        boolean isRecursive = !recursiveCtes.isEmpty();
        sql.append(isRecursive ? "WITH RECURSIVE " : "WITH ");

        List<String> cteSqlParts = new ArrayList<>();
        for (RecursiveCteDefinition r : recursiveCtes) {
            cteSqlParts.add(r.toSql());
        }
        for (CteDefinition s : standardCtes) {
            cteSqlParts.add(s.toSql());
        }

        sql.append(String.join(",\n", cteSqlParts));
        sql.append("\n");
        sql.append(finalSelectSql.strip());

        return new CteQuery(
                List.copyOf(standardCtes),
                List.copyOf(recursiveCtes),
                finalSelectSql.strip(),
                sql.toString()
        );
    }
}
