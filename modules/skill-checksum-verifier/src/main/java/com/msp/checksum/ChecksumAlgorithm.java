package com.msp.checksum;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Supported checksum algorithms.
 */
public enum ChecksumAlgorithm {
    SHA_256("SHA-256"),
    MD5("MD5"),
    CRC32("CRC32");

    private final String algorithmName;

    ChecksumAlgorithm(String algorithmName) {
        this.algorithmName = algorithmName;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public String compute(Path file) throws IOException {
        return switch (this) {
            case SHA_256 -> ChecksumVerifier.sha256(file);
            case MD5 -> ChecksumVerifier.md5(file);
            case CRC32 -> ChecksumVerifier.crc32(file);
        };
    }

    public boolean verify(Path file, String expectedHex) throws IOException {
        return switch (this) {
            case SHA_256 -> ChecksumVerifier.verifySha256(file, expectedHex);
            case MD5 -> ChecksumVerifier.verifyMd5(file, expectedHex);
            case CRC32 -> ChecksumVerifier.verifyCrc32(file, expectedHex);
        };
    }
}
