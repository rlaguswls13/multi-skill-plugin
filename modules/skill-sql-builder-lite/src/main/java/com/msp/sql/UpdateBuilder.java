package com.msp.sql;

import java.util.*;

/**
 * Fluent query builder for SQL UPDATE statements.
 */
public final class UpdateBuilder {

    private String table;
    private record SetItem(String expr, List<Object> params) {}
    private final List<SetItem> setClauses = new ArrayList<>();
    private final ConditionBuilder conditions = new ConditionBuilder();

    public UpdateBuilder() {
    }

    public UpdateBuilder(String table) {
        this.table = table;
    }

    public UpdateBuilder table(String tableName) {
        this.table = Objects.requireNonNull(tableName, "table must not be null").trim();
        return this;
    }

    public UpdateBuilder set(String column, Object value) {
        Objects.requireNonNull(column, "column must not be null");
        setClauses.add(new SetItem(column.trim() + " = ?", List.of(value)));
        return this;
    }

    public UpdateBuilder setIf(boolean condition, String column, Object value) {
        if (condition) {
            set(column, value);
        }
        return this;
    }

    public UpdateBuilder setSql(String rawExpr, Object... params) {
        Objects.requireNonNull(rawExpr, "rawExpr must not be null");
        List<Object> p = (params == null || params.length == 0) ? Collections.emptyList() : List.of(params);
        setClauses.add(new SetItem(rawExpr.trim(), p));
        return this;
    }

    // --- Dynamic WHERE Conditions ---

    public UpdateBuilder where(String clause, Object... params) {
        conditions.addAnd(true, clause, params);
        return this;
    }

    public UpdateBuilder whereIf(boolean condition, String clause, Object... params) {
        conditions.addAnd(condition, clause, params);
        return this;
    }

    public UpdateBuilder and(String clause, Object... params) {
        conditions.addAnd(true, clause, params);
        return this;
    }

    public UpdateBuilder andIf(boolean condition, String clause, Object... params) {
        conditions.addAnd(condition, clause, params);
        return this;
    }

    public UpdateBuilder or(String clause, Object... params) {
        conditions.addOr(true, clause, params);
        return this;
    }

    public UpdateBuilder orIf(boolean condition, String clause, Object... params) {
        conditions.addOr(condition, clause, params);
        return this;
    }

    public UpdateBuilder whereIn(String column, Collection<?> values) {
        conditions.addIn(true, column, values, false, ConditionBuilder.Op.AND);
        return this;
    }

    public UpdateBuilder andIn(String column, Collection<?> values) {
        conditions.addIn(true, column, values, false, ConditionBuilder.Op.AND);
        return this;
    }

    public UpdateBuilder andInIf(boolean condition, String column, Collection<?> values) {
        conditions.addIn(condition, column, values, false, ConditionBuilder.Op.AND);
        return this;
    }

    public BoundSql build() {
        if (table == null || table.isBlank()) {
            throw new IllegalStateException("Table name must be specified");
        }
        if (setClauses.isEmpty()) {
            throw new IllegalStateException("At least one SET clause must be provided");
        }

        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
        List<Object> params = new ArrayList<>();

        for (int i = 0; i < setClauses.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            SetItem item = setClauses.get(i);
            sql.append(item.expr());
            params.addAll(item.params());
        }

        conditions.appendTo(sql, params);
        return new BoundSql(sql.toString(), params);
    }
}
