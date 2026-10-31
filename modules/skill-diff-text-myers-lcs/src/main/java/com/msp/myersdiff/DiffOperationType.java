package com.msp.myersdiff;

/**
 * Type of diff operation in Myers diff edit script.
 */
public enum DiffOperationType {
    /**
     * Line preserved unchanged in both original and revised.
     */
    KEEP,

    /**
     * Line inserted into revised (does not exist in original).
     */
    INSERT,

    /**
     * Line deleted from original (does not exist in revised).
     */
    DELETE
}
