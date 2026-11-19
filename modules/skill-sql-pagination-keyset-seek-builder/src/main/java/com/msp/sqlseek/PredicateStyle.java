package com.msp.sqlseek;

/**
 * SQL predicate generation strategy for multi-column keyset pagination.
 */
public enum PredicateStyle {
    /**
     * Expanded Disjunctive Form: universal across all RDBMS and supports mixed ASC/DESC sort orders.
     * Example: {@code (a < ? OR (a = ? AND b > ?) OR (a = ? AND b = ? AND c < ?))}
     */
    EXPANDED_DISJUNCTIVE,

    /**
     * Row-Value Constructor Tuple Form: compact syntax for uniform ASC or uniform DESC sort orders
     * in databases supporting row-value comparisons (PostgreSQL, MySQL, SQLite, H2).
     * Example: {@code (a, b, c) < (?, ?, ?)}
     */
    ROW_VALUE_TUPLE
}
