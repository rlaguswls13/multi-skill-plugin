package com.msp.sql;

import java.util.*;

/**
 * Fluent query builder for SQL DELETE statements.
 */
public final class DeleteBuilder {

    private String table;
    private final ConditionBuilder conditions = new ConditionBuilder();

    public DeleteBuilder() {
    }

    public DeleteBuilder(String table) {
        this.table = table;
    }

    public DeleteBuilder from(String tableName) {
        this.table = Objects.requireNonNull(tableName, "table must not be null").trim();
        return this;
    }

    // --- Dynamic WHERE Conditions ---

    public DeleteBuilder where(String clause, Object... params) {
        conditions.addAnd(true, clause, params);
        return this;
    }

    public DeleteBuilder whereIf(boolean condition, String clause, Object... params) {
        conditions.addAnd(condition, clause, params);
        return this;
    }

    public DeleteBuilder and(String clause, Object... params) {
        conditions.addAnd(true, clause, params);
        return this;
    }

    public DeleteBuilder andIf(boolean condition, String clause, Object... params) {
        conditions.addAnd(condition, clause, params);
        return this;
    }

    public DeleteBuilder or(String clause, Object... params) {
        conditions.addOr(true, clause, params);
        return this;
    }

    public DeleteBuilder orIf(boolean condition, String clause, Object... params) {
        conditions.addOr(condition, clause, params);
        return this;
    }

    public DeleteBuilder whereIn(String column, Collection<?> values) {
        conditions.addIn(true, column, values, false, ConditionBuilder.Op.AND);
        return this;
    }

    public DeleteBuilder andIn(String column, Collection<?> values) {
        conditions.addIn(true, column, values, false, ConditionBuilder.Op.AND);
        return this;
    }

    public DeleteBuilder andInIf(boolean condition, String column, Collection<?> values) {
        conditions.addIn(condition, column, values, false, ConditionBuilder.Op.AND);
        return this;
    }

    public BoundSql build() {
        if (table == null || table.isBlank()) {
            throw new IllegalStateException("Table name must be specified via from()");
        }

        StringBuilder sql = new StringBuilder("DELETE FROM ").append(table);
        List<Object> params = new ArrayList<>();
        conditions.appendTo(sql, params);
        return new BoundSql(sql.toString(), params);
    }
}
