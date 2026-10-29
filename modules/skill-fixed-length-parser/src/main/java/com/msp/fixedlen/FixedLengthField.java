package com.msp.fixedlen;

import java.util.Objects;

/**
 * Metadata definition for a single fixed-length field.
 *
 * @param name               unique field name
 * @param byteLength         length in bytes
 * @param alignment          alignment (LEFT or RIGHT)
 * @param paddingChar        padding character used for filling remaining bytes
 * @param defaultValue       optional default value if null is provided
 * @param truncateOnOverflow whether to truncate safely if value exceeds byte length
 */
public record FixedLengthField(
        String name,
        int byteLength,
        Alignment alignment,
        char paddingChar,
        String defaultValue,
        boolean truncateOnOverflow
) {
    public FixedLengthField {
        Objects.requireNonNull(name, "Field name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Field name must not be blank");
        }
        if (byteLength <= 0) {
            throw new IllegalArgumentException("Byte length must be positive: " + byteLength);
        }
        Objects.requireNonNull(alignment, "Alignment must not be null");
    }

    public static FixedLengthField of(String name, int byteLength, Alignment alignment, char paddingChar) {
        return new FixedLengthField(name, byteLength, alignment, paddingChar, "", true);
    }

    public static FixedLengthField of(String name, int byteLength) {
        return new FixedLengthField(name, byteLength, Alignment.LEFT, ' ', "", true);
    }
}
