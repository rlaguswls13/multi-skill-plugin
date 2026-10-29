package com.msp.sql;

import java.util.*;

/**
 * Fluent query builder for SQL INSERT statements.
 */
public final class InsertBuilder {

    private String table;
    private final LinkedHashMap<String, Object> columnValues = new LinkedHashMap<>();

    public InsertBuilder() {
    }

    public InsertBuilder(String table) {
        this.table = table;
    }

    public InsertBuilder into(String tableName) {
        this.table = Objects.requireNonNull(tableName, "table must not be null").trim();
        return this;
    }

    public InsertBuilder value(String column, Object val) {
        Objects.requireNonNull(column, "column must not be null");
        this.columnValues.put(column.trim(), val);
        return this;
    }

    public InsertBuilder valueIf(boolean condition, String column, Object val) {
        if (condition) {
            value(column, val);
        }
        return this;
    }

    public InsertBuilder values(Map<String, Object> map) {
        if (map != null) {
            map.forEach(this::value);
        }
        return this;
    }

    public BoundSql build() {
        if (table == null || table.isBlank()) {
            throw new IllegalStateException("Table name must be specified via into()");
        }
        if (columnValues.isEmpty()) {
            throw new IllegalStateException("At least one column-value pair must be specified");
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
        StringBuilder placeholders = new StringBuilder("VALUES (");
        List<Object> params = new ArrayList<>(columnValues.size());

        boolean first = true;
        for (Map.Entry<String, Object> entry : columnValues.entrySet()) {
            if (!first) {
                sql.append(", ");
                placeholders.append(", ");
            }
            sql.append(entry.getKey());
            placeholders.append("?");
            params.add(entry.getValue());
            first = false;
        }

        sql.append(") ").append(placeholders).append(")");
        return new BoundSql(sql.toString(), params);
    }
}
