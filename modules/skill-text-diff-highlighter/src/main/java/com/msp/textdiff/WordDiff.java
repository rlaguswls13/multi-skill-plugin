package com.msp.textdiff;

/**
 * Represents a token or word-level diff fragment.
 *
 * @param type The change type (EQUAL, INSERT, DELETE).
 * @param text The token text content.
 */
public record WordDiff(
        DiffType type,
        String text
) {
    public boolean isChange() {
        return type != DiffType.EQUAL;
    }
}
