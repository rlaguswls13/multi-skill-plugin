package com.msp.etag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Utility to generate RFC 7232 compliant strong and weak HTTP ETags.
 */
public final class EtagGenerator {

    private static final String DEFAULT_DIGEST = "SHA-256";

    private EtagGenerator() {}

    /**
     * Generates a strong ETag based on SHA-256 hash of the given byte array.
     */
    public static Etag generateStrong(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        try {
            MessageDigest md = MessageDigest.getInstance(DEFAULT_DIGEST);
            byte[] digest = md.digest(data);
            return new Etag(HexFormat.of().formatHex(digest), false);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Digest algorithm unavailable: " + DEFAULT_DIGEST, e);
        }
    }

    /**
     * Generates a strong ETag based on SHA-256 hash of the UTF-8 text.
     */
    public static Etag generateStrong(String content) {
        Objects.requireNonNull(content, "content must not be null");
        return generateStrong(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a weak ETag based on resource length and last modified timestamp (epoch millis).
     * Output format: W/"{lengthHex}-{lastModifiedHex}"
     */
    public static Etag generateWeak(long length, long lastModifiedMillis) {
        String tag = Long.toHexString(length) + "-" + Long.toHexString(lastModifiedMillis);
        return new Etag(tag, true);
    }
}
