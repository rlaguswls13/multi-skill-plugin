package com.msp.clausesplit;

/**
 * Enumeration of standard SQL top-level clause types.
 */
public enum ClauseType {
    WITH("WITH"),
    SELECT("SELECT"),
    FROM("FROM"),
    WHERE("WHERE"),
    GROUP_BY("GROUP BY"),
    HAVING("HAVING"),
    ORDER_BY("ORDER BY"),
    LIMIT("LIMIT"),
    OFFSET("OFFSET"),
    UNION_ALL("UNION ALL"),
    UNION("UNION"),
    INSERT_INTO("INSERT INTO"),
    VALUES("VALUES"),
    UPDATE("UPDATE"),
    SET("SET"),
    DELETE_FROM("DELETE FROM"),
    RETURNING("RETURNING");

    private final String canonicalKeyword;

    ClauseType(String canonicalKeyword) {
        this.canonicalKeyword = canonicalKeyword;
    }

    public String canonicalKeyword() {
        return canonicalKeyword;
    }
}
