package com.msp.sql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Internal helper for managing dynamic WHERE conditions and IN clause expansion.
 */
final class ConditionBuilder {

    enum Op {
        AND, OR
    }

    record Condition(Op op, String clause, List<Object> params) {
    }

    private final List<Condition> conditions = new ArrayList<>();

    public void addAnd(boolean test, String clause, Object... params) {
        if (!test || clause == null || clause.isBlank()) {
            return;
        }
        List<Object> paramList = (params == null || params.length == 0) ? Collections.emptyList() : List.of(params);
        conditions.add(new Condition(Op.AND, clause.trim(), paramList));
    }

    public void addOr(boolean test, String clause, Object... params) {
        if (!test || clause == null || clause.isBlank()) {
            return;
        }
        List<Object> paramList = (params == null || params.length == 0) ? Collections.emptyList() : List.of(params);
        conditions.add(new Condition(Op.OR, clause.trim(), paramList));
    }

    public void addIn(boolean test, String column, Collection<?> values, boolean notIn, Op op) {
        if (!test || column == null || column.isBlank()) {
            return;
        }
        if (values == null || values.isEmpty()) {
            conditions.add(new Condition(op, notIn ? "1 = 1" : "1 = 0", Collections.emptyList()));
            return;
        }

        StringBuilder sb = new StringBuilder(column).append(notIn ? " NOT IN (" : " IN (");
        List<Object> paramList = new ArrayList<>(values.size());
        boolean first = true;
        for (Object val : values) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("?");
            paramList.add(val);
            first = false;
        }
        sb.append(")");
        conditions.add(new Condition(op, sb.toString(), paramList));
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }

    public void appendTo(StringBuilder sql, List<Object> outParams) {
        if (conditions.isEmpty()) {
            return;
        }
        sql.append(" WHERE ");
        for (int i = 0; i < conditions.size(); i++) {
            Condition c = conditions.get(i);
            if (i > 0) {
                sql.append(" ").append(c.op().name()).append(" ");
            }
            sql.append(c.clause());
            outParams.addAll(c.params());
        }
    }
}
