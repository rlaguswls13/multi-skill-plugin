package com.msp.checksum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksumVerifierTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Empty string known test vectors")
    void testEmptyVectors() throws IOException {
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, "");

        String expectedSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String expectedMd5 = "d41d8cd98f00b204e9800998ecf8427e";
        String expectedCrc32 = "00000000";

        assertEquals(expectedSha256, ChecksumVerifier.sha256(""));
        assertEquals(expectedSha256, ChecksumVerifier.sha256(emptyFile));
        assertEquals(expectedMd5, ChecksumVerifier.md5(""));
        assertEquals(expectedMd5, ChecksumVerifier.md5(emptyFile));
        assertEquals(expectedCrc32, ChecksumVerifier.crc32(""));
        assertEquals(expectedCrc32, ChecksumVerifier.crc32(emptyFile));
        assertEquals(0L, ChecksumVerifier.crc32Long(emptyFile));

        assertTrue(ChecksumVerifier.verifySha256(emptyFile, expectedSha256));
        assertTrue(ChecksumVerifier.verifyMd5(emptyFile, expectedMd5));
        assertTrue(ChecksumVerifier.verifyCrc32(emptyFile, expectedCrc32));
    }

    @Test
    @DisplayName("Known test vector for 'Hello, World!'")
    void testKnownStringVector() throws IOException {
        String input = "Hello, World!";
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, input, StandardCharsets.UTF_8);

        String expectedSha256 = "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f";
        String expectedMd5 = "65a8e27d8879283831b664bd8b7f0ad4";

        assertEquals(expectedSha256, ChecksumVerifier.sha256(input));
        assertEquals(expectedSha256, ChecksumVerifier.sha256(file));

        assertEquals(expectedMd5, ChecksumVerifier.md5(input));
        assertEquals(expectedMd5, ChecksumVerifier.md5(file));

        // Case-insensitive verification
        assertTrue(ChecksumVerifier.verifySha256(file, expectedSha256.toUpperCase()));
        assertTrue(ChecksumVerifier.verifyMd5(file, expectedMd5.toUpperCase()));
    }

    @Test
    @DisplayName("File integrity verification detects tampering")
    void testFileTamperingVerification() throws IOException {
        Path file = tempDir.resolve("data.bin");
        byte[] original = new byte[100_000];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) (i % 127);
        }
        Files.write(file, original);

        String sha256 = ChecksumVerifier.sha256(file);
        String md5 = ChecksumVerifier.md5(file);
        String crc32 = ChecksumVerifier.crc32(file);

        assertTrue(ChecksumVerifier.verifySha256(file, sha256));
        assertTrue(ChecksumVerifier.verifyMd5(file, md5));
        assertTrue(ChecksumVerifier.verifyCrc32(file, crc32));

        // Tamper 1 byte
        original[42] ^= 0xFF;
        Files.write(file, original);

        assertFalse(ChecksumVerifier.verifySha256(file, sha256));
        assertFalse(ChecksumVerifier.verifyMd5(file, md5));
        assertFalse(ChecksumVerifier.verifyCrc32(file, crc32));
    }

    @Test
    @DisplayName("ChecksumAlgorithm enum delegation")
    void testChecksumAlgorithmEnum() throws IOException {
        Path file = tempDir.resolve("algo.txt");
        Files.writeString(file, "Algorithm Test 2026");

        String sha = ChecksumAlgorithm.SHA_256.compute(file);
        String md5 = ChecksumAlgorithm.MD5.compute(file);
        String crc = ChecksumAlgorithm.CRC32.compute(file);

        assertTrue(ChecksumAlgorithm.SHA_256.verify(file, sha));
        assertTrue(ChecksumAlgorithm.MD5.verify(file, md5));
        assertTrue(ChecksumAlgorithm.CRC32.verify(file, crc));
    }

    @Test
    @DisplayName("Non-existent files or directories throw appropriate exceptions")
    void testErrorHandling() {
        Path nonExistent = tempDir.resolve("missing.txt");
        assertThrows(NoSuchFileException.class, () -> ChecksumVerifier.sha256(nonExistent));
        assertThrows(NoSuchFileException.class, () -> ChecksumVerifier.md5(nonExistent));
        assertThrows(NoSuchFileException.class, () -> ChecksumVerifier.crc32(nonExistent));

        assertThrows(IllegalArgumentException.class, () -> ChecksumVerifier.sha256(tempDir));
    }
}
