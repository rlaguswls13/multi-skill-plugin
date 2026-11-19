package com.msp.sqlwindow;

/**
 * Standard SQL frame exclusion clause (SQL:2003+).
 */
public enum FrameExclusion {
    NONE(""),
    EXCLUDE_CURRENT_ROW(" EXCLUDE CURRENT ROW"),
    EXCLUDE_GROUP(" EXCLUDE GROUP"),
    EXCLUDE_TIES(" EXCLUDE TIES"),
    EXCLUDE_NO_OTHERS(" EXCLUDE NO OTHERS");

    private final String sqlSnippet;

    FrameExclusion(String sqlSnippet) {
        this.sqlSnippet = sqlSnippet;
    }

    public String toSql() {
        return sqlSnippet;
    }
}
