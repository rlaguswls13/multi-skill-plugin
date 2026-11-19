package com.msp.sqlwindow;

/**
 * SQL NULLS ordering specification (NULLS FIRST / NULLS LAST).
 */
public enum NullsOrder {
    NONE(""),
    NULLS_FIRST(" NULLS FIRST"),
    NULLS_LAST(" NULLS LAST");

    private final String sqlSnippet;

    NullsOrder(String sqlSnippet) {
        this.sqlSnippet = sqlSnippet;
    }

    public String toSql() {
        return sqlSnippet;
    }
}
