package com.msp.textdiff;

/**
 * Represents a line-level diff fragment.
 *
 * @param type          The change type (EQUAL, INSERT, DELETE).
 * @param line          The line content.
 * @param oldLineNumber 1-based line number in the original text, or -1 if INSERT.
 * @param newLineNumber 1-based line number in the modified text, or -1 if DELETE.
 */
public record LineDiff(
        DiffType type,
        String line,
        int oldLineNumber,
        int newLineNumber
) {
    public boolean isChange() {
        return type != DiffType.EQUAL;
    }
}
