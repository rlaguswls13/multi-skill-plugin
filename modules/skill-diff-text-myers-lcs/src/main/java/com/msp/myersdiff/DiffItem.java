package com.msp.myersdiff;

import java.util.Objects;

/**
 * Immutable representation of a single line diff element.
 *
 * @param type       the operation type (KEEP, INSERT, or DELETE)
 * @param text       the content of the line
 * @param oldLineNum 1-based line number in original file (-1 if INSERT)
 * @param newLineNum 1-based line number in revised file (-1 if DELETE)
 */
public record DiffItem(DiffOperationType type, String text, int oldLineNum, int newLineNum) {

    public DiffItem {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }

    /**
     * Helper to check if this diff item represents an unchanged line.
     */
    public boolean isKeep() {
        return type == DiffOperationType.KEEP;
    }

    /**
     * Helper to check if this diff item represents an added line.
     */
    public boolean isInsert() {
        return type == DiffOperationType.INSERT;
    }

    /**
     * Helper to check if this diff item represents a removed line.
     */
    public boolean isDelete() {
        return type == DiffOperationType.DELETE;
    }
}
