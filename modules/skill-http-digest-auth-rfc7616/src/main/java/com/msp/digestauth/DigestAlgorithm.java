package com.msp.digestauth;

import java.util.Locale;
import java.util.Objects;

/**
 * Digest algorithms defined by RFC 7616 (and legacy RFC 2617).
 */
public enum DigestAlgorithm {
    SHA_256("SHA-256", "SHA-256", false),
    SHA_256_SESS("SHA-256-sess", "SHA-256", true),
    MD5("MD5", "MD5", false),
    MD5_SESS("MD5-sess", "MD5", true);

    private final String token;
    private final String jcaName;
    private final boolean session;

    DigestAlgorithm(String token, String jcaName, boolean session) {
        this.token = token;
        this.jcaName = jcaName;
        this.session = session;
    }

    /** Header token name as specified in RFC 7616 (e.g. {@code SHA-256}, {@code SHA-256-sess}). */
    public String token() {
        return token;
    }

    /** Underlying JCA {@link java.security.MessageDigest} algorithm name. */
    public String jcaName() {
        return jcaName;
    }

    /** Whether this algorithm is a {@code -sess} variant. */
    public boolean isSession() {
        return session;
    }

    /**
     * Parses an RFC 7616 algorithm token (case-insensitive).
     */
    public static DigestAlgorithm parse(String value) {
        Objects.requireNonNull(value, "algorithm must not be null");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SHA-256" -> SHA_256;
            case "SHA-256-SESS" -> SHA_256_SESS;
            case "MD5" -> MD5;
            case "MD5-SESS" -> MD5_SESS;
            default -> throw new DigestAuthException("Unsupported Digest algorithm: " + value);
        };
    }
}
