package com.msp.etag;

import java.util.Objects;

/**
 * Represents an HTTP Entity Tag (ETag) per RFC 7232.
 *
 * @param tag     the raw hash or token without quotes or weak prefix
 * @param isWeak  whether this is a weak validator (prefixed with W/)
 */
public record Etag(String tag, boolean isWeak) {

    public Etag {
        Objects.requireNonNull(tag, "tag must not be null");
        if (tag.isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
        // Normalize: strip leading/trailing quotes or W/ if mistakenly passed
        if (tag.startsWith("W/\"") || tag.startsWith("w/\"")) {
            tag = tag.substring(3);
            if (tag.endsWith("\"")) {
                tag = tag.substring(0, tag.length() - 1);
            }
            isWeak = true;
        } else if (tag.startsWith("\"") && tag.endsWith("\"") && tag.length() >= 2) {
            tag = tag.substring(1, tag.length() - 1);
        }
    }

    /**
     * Formats the ETag as an HTTP header value (e.g. "abc123" or W/"abc123").
     */
    public String toHeaderValue() {
        return (isWeak ? "W/\"" : "\"") + tag + "\"";
    }

    @Override
    public String toString() {
        return toHeaderValue();
    }
}
