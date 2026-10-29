package com.msp.sql;

import java.util.Collection;

/**
 * Unified facade for building fluent, dynamic SQL queries.
 */
public final class SqlBuilders {

    private SqlBuilders() {
    }

    /**
     * Creates a new SELECT query builder with specified columns.
     *
     * @param columns projection columns (or empty for "*")
     * @return SelectBuilder
     */
    public static SelectBuilder select(String... columns) {
        return new SelectBuilder().select(columns);
    }

    /**
     * Creates a new SELECT DISTINCT query builder with specified columns.
     *
     * @param columns projection columns
     * @return SelectBuilder
     */
    public static SelectBuilder selectDistinct(String... columns) {
        return new SelectBuilder().distinct().select(columns);
    }

    /**
     * Creates a new INSERT query builder for the specified table.
     *
     * @param table destination table name
     * @return InsertBuilder
     */
    public static InsertBuilder insertInto(String table) {
        return new InsertBuilder(table);
    }

    /**
     * Creates a new UPDATE query builder for the specified table.
     *
     * @param table target table name
     * @return UpdateBuilder
     */
    public static UpdateBuilder update(String table) {
        return new UpdateBuilder(table);
    }

    /**
     * Creates a new DELETE query builder for the specified table.
     *
     * @param table target table name
     * @return DeleteBuilder
     */
    public static DeleteBuilder deleteFrom(String table) {
        return new DeleteBuilder(table);
    }
}
