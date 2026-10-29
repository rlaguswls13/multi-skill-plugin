package com.msp.checksum;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * High-performance streaming checksum generator and integrity verifier for SHA-256, MD5, and CRC32.
 * Uses a streaming buffer to process files of arbitrary size with O(1) heap memory.
 */
public final class ChecksumVerifier {

    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB buffer
    private static final HexFormat HEX = HexFormat.of();

    private ChecksumVerifier() {
        // Utility class
    }

    /**
     * Computes the SHA-256 hex string of a file using streaming I/O.
     */
    public static String sha256(Path file) throws IOException {
        return digestFile(file, "SHA-256");
    }

    /**
     * Computes the SHA-256 hex string of a byte array.
     */
    public static String sha256(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        return HEX.formatHex(getDigest("SHA-256").digest(data));
    }

    /**
     * Computes the SHA-256 hex string of a UTF-8 string.
     */
    public static String sha256(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the MD5 hex string of a file using streaming I/O.
     */
    public static String md5(Path file) throws IOException {
        return digestFile(file, "MD5");
    }

    /**
     * Computes the MD5 hex string of a byte array.
     */
    public static String md5(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        return HEX.formatHex(getDigest("MD5").digest(data));
    }

    /**
     * Computes the MD5 hex string of a UTF-8 string.
     */
    public static String md5(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return md5(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the CRC32 checksum of a file formatted as an 8-digit lowercase hex string.
     */
    public static String crc32(Path file) throws IOException {
        long value = crc32Long(file);
        return String.format("%08x", value);
    }

    /**
     * Computes the raw 32-bit unsigned CRC32 value of a file.
     */
    public static long crc32Long(Path file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!Files.exists(file)) {
            throw new NoSuchFileException("File does not exist: " + file);
        }
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("Path is a directory, not a file: " + file);
        }

        CRC32 crc = new CRC32();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream is = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(is)) {
            int read;
            while ((read = bis.read(buffer)) != -1) {
                crc.update(buffer, 0, read);
            }
        }
        return crc.getValue();
    }

    /**
     * Computes the CRC32 checksum of a byte array as an 8-digit hex string.
     */
    public static String crc32(byte[] data) {
        return String.format("%08x", crc32Long(data));
    }

    /**
     * Computes the raw CRC32 unsigned long value of a byte array.
     */
    public static long crc32Long(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    /**
     * Computes the CRC32 checksum of a UTF-8 string as an 8-digit hex string.
     */
    public static String crc32(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return crc32(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Verifies if the file's SHA-256 matches the expected hex string (case-insensitive).
     */
    public static boolean verifySha256(Path file, String expectedHex) throws IOException {
        Objects.requireNonNull(expectedHex, "expectedHex must not be null");
        return sha256(file).equalsIgnoreCase(expectedHex.trim());
    }

    /**
     * Verifies if the byte array's SHA-256 matches the expected hex string (case-insensitive).
     */
    public static boolean verifySha256(byte[] data, String expectedHex) {
        Objects.requireNonNull(expectedHex, "expectedHex must not be null");
        return sha256(data).equalsIgnoreCase(expectedHex.trim());
    }

    /**
     * Verifies if the file's MD5 matches the expected hex string (case-insensitive).
     */
    public static boolean verifyMd5(Path file, String expectedHex) throws IOException {
        Objects.requireNonNull(expectedHex, "expectedHex must not be null");
        return md5(file).equalsIgnoreCase(expectedHex.trim());
    }

    /**
     * Verifies if the byte array's MD5 matches the expected hex string (case-insensitive).
     */
    public static boolean verifyMd5(byte[] data, String expectedHex) {
        Objects.requireNonNull(expectedHex, "expectedHex must not be null");
        return md5(data).equalsIgnoreCase(expectedHex.trim());
    }

    /**
     * Verifies if the file's CRC32 matches the expected hex string (case-insensitive, trims leading zeros if formatted without them).
     */
    public static boolean verifyCrc32(Path file, String expectedHex) throws IOException {
        Objects.requireNonNull(expectedHex, "expectedHex must not be null");
        String actual = crc32(file);
        String expected = expectedHex.trim();
        // Allow leading 0 padding differences (e.g. "a" vs "0000000a")
        if (actual.equalsIgnoreCase(expected)) {
            return true;
        }
        try {
            long expectedLong = Long.parseUnsignedLong(expected, 16);
            return crc32Long(file) == expectedLong;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String digestFile(Path file, String algorithm) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!Files.exists(file)) {
            throw new NoSuchFileException("File does not exist: " + file);
        }
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("Path is a directory, not a file: " + file);
        }

        MessageDigest digest = getDigest(algorithm);
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream is = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(is)) {
            int read;
            while ((read = bis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HEX.formatHex(digest.digest());
    }

    private static MessageDigest getDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Security provider does not support algorithm: " + algorithm, e);
        }
    }
}
