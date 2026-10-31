package com.msp.cookiejar;

import java.util.Locale;

/**
 * RFC 6265bis SameSite attribute values.
 */
public enum SameSite {
    STRICT,
    LAX,
    NONE;

    public static SameSite fromString(String value) {
        if (value == null) {
            return LAX;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "STRICT" -> STRICT;
            case "NONE" -> NONE;
            default -> LAX;
        };
    }
}
