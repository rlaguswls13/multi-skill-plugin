package com.msp.ahocorasick;

import java.util.Objects;

/**
 * Represents a matched keyword occurrence in the scanned text.
 *
 * @param keyword the matched pattern/keyword
 * @param start   inclusive starting character index in the text
 * @param end     exclusive ending character index in the text
 * @param payload optional user payload or tag associated with the pattern
 */
public record PatternMatch(String keyword, int start, int end, Object payload) {

    public PatternMatch {
        Objects.requireNonNull(keyword, "keyword must not be null");
        if (start < 0) {
            throw new IllegalArgumentException("start must be >= 0");
        }
        if (end < start) {
            throw new IllegalArgumentException("end must be >= start");
        }
    }

    /**
     * Convenience constructor without payload.
     */
    public PatternMatch(String keyword, int start, int end) {
        this(keyword, start, end, null);
    }

    /**
     * Length of the matched range.
     */
    public int length() {
        return end - start;
    }
}
