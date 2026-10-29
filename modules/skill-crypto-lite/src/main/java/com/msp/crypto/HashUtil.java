package com.msp.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * High-performance SHA-256 and SHA-512 hashing utilities.
 * Uses Java 21 {@link HexFormat} for zero-allocation hexadecimal formatting and parsing.
 */
public final class HashUtil {

    private static final HexFormat HEX = HexFormat.of();

    private HashUtil() {
        // Utility class
    }

    /**
     * Computes the SHA-256 digest of byte array.
     */
    public static byte[] sha256(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Computes the SHA-256 digest as a lowercase hexadecimal string.
     */
    public static String sha256Hex(byte[] data) {
        return HEX.formatHex(sha256(data));
    }

    /**
     * Computes the SHA-256 digest of a UTF-8 string as lowercase hexadecimal.
     */
    public static String sha256Hex(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the SHA-512 digest of byte array.
     */
    public static byte[] sha512(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("SHA-512 algorithm not available", e);
        }
    }

    /**
     * Computes the SHA-512 digest as a lowercase hexadecimal string.
     */
    public static String sha512Hex(byte[] data) {
        return HEX.formatHex(sha512(data));
    }

    /**
     * Computes the SHA-512 digest of a UTF-8 string as lowercase hexadecimal.
     */
    public static String sha512Hex(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return sha512Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Formats bytes as a lowercase hexadecimal string.
     */
    public static String bytesToHex(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        return HEX.formatHex(bytes);
    }

    /**
     * Parses a hexadecimal string into a byte array.
     */
    public static byte[] hexToBytes(String hex) {
        Objects.requireNonNull(hex, "hex must not be null");
        return HEX.parseHex(hex);
    }
}
