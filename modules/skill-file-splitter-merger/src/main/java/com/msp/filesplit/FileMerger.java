package com.msp.filesplit;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Exact byte-level file chunk merger with optional SHA-256 integrity verification.
 */
public final class FileMerger {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final HexFormat HEX = HexFormat.of();

    private FileMerger() {
        // Utility class
    }

    /**
     * Merges an ordered list of chunk files into destinationFile.
     *
     * @param chunks          ordered list of chunk paths
     * @param destinationFile destination file path
     * @return destinationFile path
     * @throws IOException if an I/O error occurs
     */
    public static Path merge(List<Path> chunks, Path destinationFile) throws IOException {
        Objects.requireNonNull(chunks, "chunks must not be null");
        Objects.requireNonNull(destinationFile, "destinationFile must not be null");

        Path parent = destinationFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream fos = Files.newOutputStream(destinationFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            for (Path chunk : chunks) {
                if (!Files.exists(chunk)) {
                    throw new NoSuchFileException("Chunk file does not exist: " + chunk);
                }
                try (InputStream fis = Files.newInputStream(chunk);
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    int read;
                    while ((read = bis.read(buffer)) != -1) {
                        bos.write(buffer, 0, read);
                    }
                }
            }
        }

        return destinationFile;
    }

    /**
     * Merges chunk files into destinationFile and verifies that the resulting file's
     * SHA-256 checksum matches expectedSha256.
     *
     * @param chunks          ordered list of chunk paths
     * @param destinationFile destination file path
     * @param expectedSha256  expected 64-character SHA-256 hex string
     * @return destinationFile path
     * @throws IOException           if an I/O error occurs
     * @throws IllegalStateException if SHA-256 verification fails
     */
    public static Path merge(List<Path> chunks, Path destinationFile, String expectedSha256) throws IOException {
        Objects.requireNonNull(expectedSha256, "expectedSha256 must not be null");
        merge(chunks, destinationFile);
        String actualSha256 = computeSha256(destinationFile);
        if (!actualSha256.equalsIgnoreCase(expectedSha256.trim())) {
            throw new IllegalStateException("Merged file SHA-256 mismatch! Expected: " + expectedSha256 + ", but got: " + actualSha256);
        }
        return destinationFile;
    }

    /**
     * Computes the SHA-256 hex string of a file using streaming I/O.
     */
    public static String computeSha256(Path file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!Files.exists(file)) {
            throw new NoSuchFileException("File does not exist: " + file);
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream is = Files.newInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(is)) {
                int read;
                while ((read = bis.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HEX.formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in security provider", e);
        }
    }
}
