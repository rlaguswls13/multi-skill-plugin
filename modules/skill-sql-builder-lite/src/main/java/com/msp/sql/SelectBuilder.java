package com.msp.sql;

import java.util.*;

/**
 * Fluent query builder for SQL SELECT statements with dynamic conditions,
 * IN-clause expansion, JOINs, paging, and ordering.
 */
public final class SelectBuilder {

    private final List<String> columns = new ArrayList<>();
    private boolean distinct = false;
    private String table;
    private final List<String> joins = new ArrayList<>();
    private final ConditionBuilder conditions = new ConditionBuilder();
    private final List<String> groupBys = new ArrayList<>();
    private String havingClause;
    private final List<Object> havingParams = new ArrayList<>();
    private final List<String> orderBys = new ArrayList<>();
    private Integer limit;
    private Integer offset;

    public SelectBuilder() {
    }

    public SelectBuilder select(String... cols) {
        if (cols != null && cols.length > 0) {
            Collections.addAll(this.columns, cols);
        }
        return this;
    }

    public SelectBuilder distinct() {
        this.distinct = true;
        return this;
    }

    public SelectBuilder distinct(boolean isDistinct) {
        this.distinct = isDistinct;
        return this;
    }

    public SelectBuilder from(String fromTable) {
        this.table = Objects.requireNonNull(fromTable, "table must not be null").trim();
        return this;
    }

    public SelectBuilder join(String joinClause) {
        if (joinClause != null && !joinClause.isBlank()) {
            this.joins.add(joinClause.trim());
        }
        return this;
    }

    public SelectBuilder innerJoin(String joinTable, String onCondition) {
        this.joins.add("INNER JOIN " + joinTable.trim() + " ON " + onCondition.trim());
        return this;
    }

    public SelectBuilder leftJoin(String joinTable, String onCondition) {
        this.joins.add("LEFT JOIN " + joinTable.trim() + " ON " + onCondition.trim());
        return this;
    }

    public SelectBuilder rightJoin(String joinTable, String onCondition) {
        this.joins.add("RIGHT JOIN " + joinTable.trim() + " ON " + onCondition.trim());
        return this;
    }

    // --- Dynamic WHERE Conditions ---

    public SelectBuilder where(String clause, Object... params) {
        conditions.addAnd(true, clause, params);
        return this;
    }

    public SelectBuilder whereIf(boolean condition, String clause, Object... params) {
        conditions.addAnd(condition, clause, params);
        return this;
    }

    public SelectBuilder and(String clause, Object... params) {
        conditions.addAnd(true, clause, params);
        return this;
    }

    public SelectBuilder andIf(boolean condition, String clause, Object... params) {
        conditions.addAnd(condition, clause, params);
        return this;
    }

    public SelectBuilder or(String clause, Object... params) {
        conditions.addOr(true, clause, params);
        return this;
    }

    public SelectBuilder orIf(boolean condition, String clause, Object... params) {
        conditions.addOr(condition, clause, params);
        return this;
    }

    // --- IN Clause Auto-Expansion ---

    public SelectBuilder whereIn(String column, Collection<?> values) {
        conditions.addIn(true, column, values, false, ConditionBuilder.Op.AND);
        return this;
    }

    public SelectBuilder whereInIf(boolean condition, String column, Collection<?> values) {
        conditions.addIn(condition, column, values, false, ConditionBuilder.Op.AND);
        return this;
    }

    public SelectBuilder andIn(String column, Collection<?> values) {
        conditions.addIn(true, column, values, false, ConditionBuilder.Op.AND);
        return this;
    }

    public SelectBuilder andInIf(boolean condition, String column, Collection<?> values) {
        conditions.addIn(condition, column, values, false, ConditionBuilder.Op.AND);
        return this;
    }

    public SelectBuilder orIn(String column, Collection<?> values) {
        conditions.addIn(true, column, values, false, ConditionBuilder.Op.OR);
        return this;
    }

    public SelectBuilder orInIf(boolean condition, String column, Collection<?> values) {
        conditions.addIn(condition, column, values, false, ConditionBuilder.Op.OR);
        return this;
    }

    public SelectBuilder whereNotIn(String column, Collection<?> values) {
        conditions.addIn(true, column, values, true, ConditionBuilder.Op.AND);
        return this;
    }

    public SelectBuilder andNotIn(String column, Collection<?> values) {
        conditions.addIn(true, column, values, true, ConditionBuilder.Op.AND);
        return this;
    }

    // --- Group By & Having ---

    public SelectBuilder groupBy(String... cols) {
        if (cols != null && cols.length > 0) {
            Collections.addAll(this.groupBys, cols);
        }
        return this;
    }

    public SelectBuilder having(String clause, Object... params) {
        this.havingClause = clause;
        if (params != null && params.length > 0) {
            Collections.addAll(this.havingParams, params);
        }
        return this;
    }

    public SelectBuilder havingIf(boolean condition, String clause, Object... params) {
        if (condition) {
            having(clause, params);
        }
        return this;
    }

    // --- Order By ---

    public SelectBuilder orderBy(String... orders) {
        if (orders != null && orders.length > 0) {
            Collections.addAll(this.orderBys, orders);
        }
        return this;
    }

    // --- Paging & Limits ---

    public SelectBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    public SelectBuilder offset(int offset) {
        this.offset = offset;
        return this;
    }

    /**
     * Sets 1-based page and page size.
     * Example: page=1, size=10 -> LIMIT 10 OFFSET 0
     *
     * @param page page index (>= 1)
     * @param size page size (>= 1)
     * @return this builder
     */
    public SelectBuilder page(int page, int size) {
        int actualPage = Math.max(1, page);
        int actualSize = Math.max(1, size);
        this.limit = actualSize;
        this.offset = (actualPage - 1) * actualSize;
        return this;
    }

    /**
     * Builds and returns the parameterized {@link BoundSql}.
     *
     * @return immutable BoundSql record
     */
    public BoundSql build() {
        if (table == null || table.isBlank()) {
            throw new IllegalStateException("Table name must be specified via from()");
        }

        StringBuilder sql = new StringBuilder("SELECT ");
        if (distinct) {
            sql.append("DISTINCT ");
        }

        if (columns.isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", columns));
        }

        sql.append(" FROM ").append(table);

        for (String j : joins) {
            sql.append(" ").append(j);
        }

        List<Object> params = new ArrayList<>();
        conditions.appendTo(sql, params);

        if (!groupBys.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupBys));
            if (havingClause != null && !havingClause.isBlank()) {
                sql.append(" HAVING ").append(havingClause.trim());
                params.addAll(havingParams);
            }
        }

        if (!orderBys.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", orderBys));
        }

        if (limit != null) {
            sql.append(" LIMIT ?");
            params.add(limit);
        }

        if (offset != null) {
            sql.append(" OFFSET ?");
            params.add(offset);
        }

        return new BoundSql(sql.toString(), params);
    }
}
