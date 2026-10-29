package com.msp.codec;

import java.util.Base64;
import java.util.HexFormat;

/**
 * Unified high-performance codec suite for Base64, Base64URL, Hex, and Base32.
 * Pure Java 21 with zero external dependencies.
 */
public final class Codecs {

    private static final HexFormat HEX_LOWER = HexFormat.of();
    private static final HexFormat HEX_UPPER = HexFormat.of().withUpperCase();
    private static final Base64.Encoder B64_ENC = Base64.getEncoder();
    private static final Base64.Decoder B64_DEC = Base64.getDecoder();
    private static final Base64.Encoder B64_URL_ENC = Base64.getUrlEncoder();
    private static final Base64.Encoder B64_URL_ENC_NO_PAD = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_URL_DEC = Base64.getUrlDecoder();

    private Codecs() {}

    // --- Hex ---

    /**
     * Converts a byte array to a lowercase hexadecimal string.
     */
    public static String toHex(byte[] bytes) {
        if (bytes == null) return "";
        return HEX_LOWER.formatHex(bytes);
    }

    /**
     * Converts a byte array to a hexadecimal string with configurable casing.
     */
    public static String toHex(byte[] bytes, boolean uppercase) {
        if (bytes == null) return "";
        return uppercase ? HEX_UPPER.formatHex(bytes) : HEX_LOWER.formatHex(bytes);
    }

    /**
     * Decodes a hexadecimal string to a byte array.
     */
    public static byte[] fromHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
        try {
            return HEX_LOWER.parseHex(hex);
        } catch (IllegalArgumentException e) {
            throw new CodecException("Failed to decode Hex string: " + e.getMessage(), e);
        }
    }

    // --- Base64 ---

    /**
     * Encodes byte array to standard Base64 string (RFC 4648).
     */
    public static String toBase64(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        return B64_ENC.encodeToString(bytes);
    }

    /**
     * Decodes standard Base64 string to byte array.
     */
    public static byte[] fromBase64(String str) {
        if (str == null || str.isEmpty()) return new byte[0];
        try {
            return B64_DEC.decode(str.trim());
        } catch (IllegalArgumentException e) {
            throw new CodecException("Failed to decode Base64 string: " + e.getMessage(), e);
        }
    }

    // --- Base64URL ---

    /**
     * Encodes byte array to URL-safe Base64 string without padding.
     */
    public static String toBase64Url(byte[] bytes) {
        return toBase64Url(bytes, true);
    }

    /**
     * Encodes byte array to URL-safe Base64 string with optional padding omission.
     */
    public static String toBase64Url(byte[] bytes, boolean withoutPadding) {
        if (bytes == null || bytes.length == 0) return "";
        return withoutPadding ? B64_URL_ENC_NO_PAD.encodeToString(bytes) : B64_URL_ENC.encodeToString(bytes);
    }

    /**
     * Decodes URL-safe Base64 string to byte array.
     */
    public static byte[] fromBase64Url(String str) {
        if (str == null || str.isEmpty()) return new byte[0];
        try {
            return B64_URL_DEC.decode(str.trim());
        } catch (IllegalArgumentException e) {
            throw new CodecException("Failed to decode Base64URL string: " + e.getMessage(), e);
        }
    }

    // --- Base32 ---

    /**
     * Encodes byte array to RFC 4648 Base32 string with padding.
     */
    public static String toBase32(byte[] bytes) {
        return Base32.encodeRfc4648(bytes, true);
    }

    /**
     * Encodes byte array to RFC 4648 Base32 string with optional padding.
     */
    public static String toBase32(byte[] bytes, boolean withPadding) {
        return Base32.encodeRfc4648(bytes, withPadding);
    }

    /**
     * Decodes RFC 4648 Base32 string.
     */
    public static byte[] fromBase32(String str) {
        return Base32.decodeRfc4648(str);
    }

    /**
     * Encodes byte array to Crockford Base32 string.
     */
    public static String toCrockfordBase32(byte[] bytes) {
        return Base32.encodeCrockford(bytes);
    }

    /**
     * Decodes Crockford Base32 string (case-insensitive, tolerates hyphens and I/L/O aliases).
     */
    public static byte[] fromCrockfordBase32(String str) {
        return Base32.decodeCrockford(str);
    }
}
