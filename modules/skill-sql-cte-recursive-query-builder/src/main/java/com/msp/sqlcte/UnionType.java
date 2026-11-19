package com.msp.sqlcte;

/**
 * Union type for combining anchor and recursive members in recursive CTE queries.
 */
public enum UnionType {
    UNION_ALL("UNION ALL"),
    UNION("UNION");

    private final String keyword;

    UnionType(String keyword) {
        this.keyword = keyword;
    }

    public String keyword() {
        return keyword;
    }
}
