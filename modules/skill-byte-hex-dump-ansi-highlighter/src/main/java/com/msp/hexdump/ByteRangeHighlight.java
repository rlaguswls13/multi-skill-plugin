package com.msp.hexdump;

import java.util.Objects;

/**
 * Defines a specific byte index range to highlight with custom ANSI color and optional label.
 *
 * @param offset Starting byte index (inclusive)
 * @param length Number of bytes in this range
 * @param color  ANSI color to apply to hex and ASCII columns
 * @param label  Optional human-readable label or description
 */
public record ByteRangeHighlight(
    int offset,
    int length,
    AnsiColor color,
    String label
) {
    public ByteRangeHighlight {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        Objects.requireNonNull(color, "color cannot be null");
    }

    public ByteRangeHighlight(int offset, int length, AnsiColor color) {
        this(offset, length, color, null);
    }

    public boolean contains(int index) {
        return index >= offset && index < (offset + length);
    }
}
