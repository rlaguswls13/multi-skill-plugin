package com.msp.httprange;

import java.util.Objects;

/**
 * Represents an HTTP byte range for range requests (RFC 7233 / RFC 9110).
 *
 * @param start         inclusive start byte offset (0-indexed)
 * @param end           inclusive end byte offset (0-indexed)
 * @param totalLength   total representation length in bytes
 * @param isSatisfiable whether this byte range is satisfiable within the given total length
 */
public record ByteRange(long start, long end, long totalLength, boolean isSatisfiable) {

    public ByteRange {
        if (totalLength < 0) {
            throw new IllegalArgumentException("totalLength cannot be negative: " + totalLength);
        }
    }

    /**
     * Convenience constructor evaluating satisfiability based on offsets and total length.
     */
    public ByteRange(long start, long end, long totalLength) {
        this(
            start,
            end,
            totalLength,
            totalLength > 0 && start >= 0 && end >= start && start < totalLength
        );
    }

    /**
     * Creates a satisfiable ByteRange.
     */
    public static ByteRange satisfiable(long start, long end, long totalLength) {
        if (totalLength <= 0 || start < 0 || end < start || start >= totalLength) {
            throw new IllegalArgumentException(
                "Invalid satisfiable range: start=" + start + ", end=" + end + ", totalLength=" + totalLength
            );
        }
        long clampedEnd = Math.min(end, totalLength - 1);
        return new ByteRange(start, clampedEnd, totalLength, true);
    }

    /**
     * Creates an unsatisfiable ByteRange (HTTP 416 Range Not Satisfiable).
     */
    public static ByteRange unsatisfiable(long totalLength) {
        return new ByteRange(-1, -1, totalLength, false);
    }

    /**
     * Returns the length of the byte chunk in bytes (end - start + 1).
     * If not satisfiable, returns 0.
     */
    public long length() {
        if (!isSatisfiable) {
            return 0;
        }
        return end - start + 1;
    }

    /**
     * HTTP status code: 206 (Partial Content) if satisfiable, 416 (Range Not Satisfiable) otherwise.
     */
    public int httpStatus() {
        return isSatisfiable ? 206 : 416;
    }

    /**
     * Formats this range for the HTTP {@code Content-Range} response header.
     * <p>
     * Satisfiable: {@code "bytes <start>-<end>/<totalLength>"} (e.g. {@code "bytes 0-499/1234"})
     * Unsatisfiable: {@code "bytes *" + "/<totalLength>"} (e.g. {@code "bytes *" + "/1234"})
     * </p>
     */
    public String toContentRangeHeader() {
        if (isSatisfiable) {
            return "bytes " + start + "-" + end + "/" + totalLength;
        } else {
            return "bytes */" + totalLength;
        }
    }
}
