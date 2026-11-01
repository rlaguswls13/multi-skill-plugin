package com.msp.worddiff;

import java.util.Objects;

/**
 * Represents an edit item consisting of a diff operation and associated text.
 *
 * @param op   diff operation (EQUAL, DELETE, INSERT)
 * @param text text content for this diff segment
 */
public record WordDiffItem(WordDiffOp op, String text) {

    public WordDiffItem {
        Objects.requireNonNull(op, "op must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }
}
