package com.msp.httprange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses HTTP {@code Range} request headers in compliance with RFC 7233 / RFC 9110.
 * <p>
 * Supported formats:
 * <ul>
 *   <li>{@code "bytes=0-499"} (explicit range)</li>
 *   <li>{@code "bytes=500-"} (from offset to the end of resource)</li>
 *   <li>{@code "bytes=-500"} (suffix: last 500 bytes of resource)</li>
 *   <li>Comma-separated multiple ranges: {@code "bytes=0-50, 100-200"}</li>
 * </ul>
 * </p>
 */
public final class RangeHeaderParser {

    private static final String BYTES_PREFIX = "bytes=";

    private RangeHeaderParser() {
        // Utility class
    }

    /**
     * Parses the single or first byte range from the {@code Range} header.
     *
     * @param rangeHeader the value of the {@code Range} HTTP header (e.g., {@code "bytes=0-499"})
     * @param totalLength total size of the resource in bytes
     * @return a {@link ByteRange} object; if unsatisfiable or unparseable, returns an unsatisfiable range (HTTP 416)
     */
    public static ByteRange parse(String rangeHeader, long totalLength) {
        List<ByteRange> ranges = parseMultiple(rangeHeader, totalLength);
        if (ranges.isEmpty()) {
            return ByteRange.unsatisfiable(Math.max(0, totalLength));
        }
        return ranges.getFirst();
    }

    /**
     * Parses all byte ranges from the {@code Range} header (for multipart/byteranges support).
     *
     * @param rangeHeader the value of the {@code Range} HTTP header
     * @param totalLength total size of the resource in bytes
     * @return an unmodifiable list of {@link ByteRange}s, or a list containing a single unsatisfiable range if invalid
     */
    public static List<ByteRange> parseMultiple(String rangeHeader, long totalLength) {
        if (totalLength <= 0 || rangeHeader == null || rangeHeader.isBlank()) {
            return Collections.singletonList(ByteRange.unsatisfiable(Math.max(0, totalLength)));
        }

        String trimmed = rangeHeader.trim();
        int eqIndex = trimmed.indexOf('=');
        if (eqIndex == -1) {
            return Collections.singletonList(ByteRange.unsatisfiable(totalLength));
        }

        String unit = trimmed.substring(0, eqIndex).trim();
        if (!unit.equalsIgnoreCase("bytes")) {
            return Collections.singletonList(ByteRange.unsatisfiable(totalLength));
        }

        String rangesSpec = trimmed.substring(eqIndex + 1).trim();
        if (rangesSpec.isEmpty()) {
            return Collections.singletonList(ByteRange.unsatisfiable(totalLength));
        }

        String[] parts = rangesSpec.split(",");
        List<ByteRange> result = new ArrayList<>();

        for (String part : parts) {
            String spec = part.trim();
            if (spec.isEmpty()) {
                continue;
            }
            ByteRange parsed = parseSingleSpec(spec, totalLength);
            result.add(parsed);
        }

        if (result.isEmpty()) {
            return Collections.singletonList(ByteRange.unsatisfiable(totalLength));
        }

        return Collections.unmodifiableList(result);
    }

    private static ByteRange parseSingleSpec(String spec, long totalLength) {
        try {
            int dashIndex = spec.indexOf('-');
            if (dashIndex == -1) {
                return ByteRange.unsatisfiable(totalLength);
            }

            // Case 1: Suffix range "-500" (last N bytes)
            if (dashIndex == 0) {
                long suffix = Long.parseLong(spec.substring(1).trim());
                if (suffix <= 0) {
                    return ByteRange.unsatisfiable(totalLength);
                }
                long start = Math.max(0, totalLength - suffix);
                long end = totalLength - 1;
                return ByteRange.satisfiable(start, end, totalLength);
            }

            String startPart = spec.substring(0, dashIndex).trim();
            long start = Long.parseLong(startPart);

            // Case 2: Open-ended range "500-" (from start to end of resource)
            if (dashIndex == spec.length() - 1) {
                if (start < 0 || start >= totalLength) {
                    return ByteRange.unsatisfiable(totalLength);
                }
                return ByteRange.satisfiable(start, totalLength - 1, totalLength);
            }

            // Case 3: Explicit range "0-499"
            String endPart = spec.substring(dashIndex + 1).trim();
            long end = Long.parseLong(endPart);

            if (start < 0 || end < start || start >= totalLength) {
                return ByteRange.unsatisfiable(totalLength);
            }

            long clampedEnd = Math.min(end, totalLength - 1);
            return ByteRange.satisfiable(start, clampedEnd, totalLength);

        } catch (NumberFormatException | ArithmeticException e) {
            return ByteRange.unsatisfiable(totalLength);
        }
    }
}
