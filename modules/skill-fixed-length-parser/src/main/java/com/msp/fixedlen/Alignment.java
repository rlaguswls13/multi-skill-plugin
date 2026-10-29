package com.msp.fixedlen;

/**
 * Alignment for fixed-length fields.
 */
public enum Alignment {
    /**
     * Value aligned to the left, padding applied on the right.
     * Typical for text fields padded with spaces.
     */
    LEFT,

    /**
     * Value aligned to the right, padding applied on the left.
     * Typical for numeric fields padded with leading zeros or spaces.
     */
    RIGHT
}
