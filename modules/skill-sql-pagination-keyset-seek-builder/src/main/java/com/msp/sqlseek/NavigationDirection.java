package com.msp.sqlseek;

/**
 * Navigation direction for keyset pagination.
 */
public enum NavigationDirection {
    /**
     * Fetch records after the current cursor following the natural sort order.
     */
    NEXT_PAGE,

    /**
     * Fetch records before the current cursor by inverting the sort directions.
     */
    PREVIOUS_PAGE
}
