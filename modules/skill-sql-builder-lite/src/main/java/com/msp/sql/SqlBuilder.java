package com.msp.sql;

/**
 * Convenient entry point alias for {@link SqlBuilders}.
 */
public final class SqlBuilder {

    private SqlBuilder() {
    }

    public static SelectBuilder select(String... columns) {
        return SqlBuilders.select(columns);
    }

    public static SelectBuilder selectDistinct(String... columns) {
        return SqlBuilders.selectDistinct(columns);
    }

    public static InsertBuilder insertInto(String table) {
        return SqlBuilders.insertInto(table);
    }

    public static UpdateBuilder update(String table) {
        return SqlBuilders.update(table);
    }

    public static DeleteBuilder deleteFrom(String table) {
        return SqlBuilders.deleteFrom(table);
    }
}
