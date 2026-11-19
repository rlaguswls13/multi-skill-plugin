package com.msp.sqlwindow;

import java.util.Objects;

/**
 * Representation of an ORDER BY expression column and direction.
 */
public record OrderSpec(String column, SortDirection direction, NullsOrder nullsOrder) {

    public OrderSpec {
        Objects.requireNonNull(column, "column cannot be null");
        Objects.requireNonNull(direction, "direction cannot be null");
        Objects.requireNonNull(nullsOrder, "nullsOrder cannot be null");
    }

    public static OrderSpec asc(String column) {
        return new OrderSpec(column, SortDirection.ASC, NullsOrder.NONE);
    }

    public static OrderSpec ascNullsFirst(String column) {
        return new OrderSpec(column, SortDirection.ASC, NullsOrder.NULLS_FIRST);
    }

    public static OrderSpec ascNullsLast(String column) {
        return new OrderSpec(column, SortDirection.ASC, NullsOrder.NULLS_LAST);
    }

    public static OrderSpec desc(String column) {
        return new OrderSpec(column, SortDirection.DESC, NullsOrder.NONE);
    }

    public static OrderSpec descNullsFirst(String column) {
        return new OrderSpec(column, SortDirection.DESC, NullsOrder.NULLS_FIRST);
    }

    public static OrderSpec descNullsLast(String column) {
        return new OrderSpec(column, SortDirection.DESC, NullsOrder.NULLS_LAST);
    }

    public String toSql() {
        return column + " " + direction.name() + nullsOrder.toSql();
    }
}
