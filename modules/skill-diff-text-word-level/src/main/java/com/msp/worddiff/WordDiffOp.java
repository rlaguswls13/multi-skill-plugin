package com.msp.worddiff;

/**
 * Diff operation applied to a token or segment.
 */
public enum WordDiffOp {
    /**
     * Unchanged text between original and revised versions.
     */
    EQUAL,

    /**
     * Text deleted from the original version.
     */
    DELETE,

    /**
     * Text inserted into the revised version.
     */
    INSERT
}
