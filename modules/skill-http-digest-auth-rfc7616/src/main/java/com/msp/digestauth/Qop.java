package com.msp.digestauth;

import java.util.Locale;
import java.util.Objects;

/**
 * Quality of Protection (qop) directives in RFC 7616 HTTP Digest Authentication.
 */
public enum Qop {
    AUTH("auth"),
    AUTH_INT("auth-int"),
    NONE("");

    private final String token;

    Qop(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static Qop parse(String value) {
        Objects.requireNonNull(value, "qop must not be null");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "auth" -> AUTH;
            case "auth-int" -> AUTH_INT;
            case "" -> NONE;
            default -> throw new DigestAuthException("Unsupported qop value: " + value);
        };
    }
}
