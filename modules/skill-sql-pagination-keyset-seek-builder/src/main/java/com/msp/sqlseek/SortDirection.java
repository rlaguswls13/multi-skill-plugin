package com.msp.sqlseek;

/**
 * Sort direction for SQL pagination columns.
 */
public enum SortDirection {
    ASC,
    DESC;

    /**
     * Returns the opposite sort direction (used for PREVIOUS_PAGE navigation).
     */
    public SortDirection invert() {
        return this == ASC ? DESC : ASC;
    }

    /**
     * Returns the strict inequality operator associated with moving forward in this sort direction.
     * ASC -> ">" (next items are greater than cursor)
     * DESC -> "<" (next items are less than cursor)
     */
    public String inequalityOperator() {
        return this == ASC ? ">" : "<";
    }
}
