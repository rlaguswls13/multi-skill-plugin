package com.msp.filesearch;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a matched occurrence in a file.
 *
 * @param path        file path where match was found
 * @param lineNumber  1-based line number of the matching line
 * @param lineContent complete text of the matching line
 * @param matchStart  0-based start character index of the match within lineContent
 * @param matchEnd    0-based end character index (exclusive) of the match within lineContent
 */
public record SearchResult(
        Path path,
        int lineNumber,
        String lineContent,
        int matchStart,
        int matchEnd
) {
    public SearchResult {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(lineContent, "lineContent must not be null");
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be 1 or greater");
        }
        if (matchStart < 0 || matchEnd < matchStart || matchEnd > lineContent.length()) {
            throw new IllegalArgumentException(String.format(
                    "Invalid match bounds [%d, %d] for string length %d",
                    matchStart, matchEnd, lineContent.length()));
        }
    }

    /**
     * Extracts the exact matched substring from lineContent.
     */
    public String matchedText() {
        return lineContent.substring(matchStart, matchEnd);
    }
}
