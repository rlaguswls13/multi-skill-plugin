package com.msp.hangul;

/**
 * Represents a matched substring range in the target text.
 *
 * @param start       inclusive start index in the target text
 * @param end         exclusive end index in the target text
 * @param matchedText the actual matched substring
 */
public record MatchRange(int start, int end, String matchedText) {
    public MatchRange {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid match range: [" + start + ", " + end + ")");
        }
    }
}
