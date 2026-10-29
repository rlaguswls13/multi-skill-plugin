package com.msp.zip;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipCompressorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Single file compression and decompression round-trip")
    void testSingleFileCompressionAndDecompression() throws IOException {
        Path sourceFile = tempDir.resolve("sample.txt");
        String originalContent = "Hello Java 21 ZIP compression world! Multi-skill-plugin 2026";
        Files.writeString(sourceFile, originalContent, StandardCharsets.UTF_8);

        Path zipFile = tempDir.resolve("sample.zip");
        ZipCompressor.compressFile(sourceFile, zipFile);

        assertTrue(Files.exists(zipFile));
        assertTrue(Files.size(zipFile) > 0);

        Path extractDir = tempDir.resolve("extracted");
        ZipCompressor.decompress(zipFile, extractDir, false);

        Path extractedFile = extractDir.resolve("sample.txt");
        assertTrue(Files.exists(extractedFile));
        assertEquals(originalContent, Files.readString(extractedFile, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Directory compression without root and with root")
    void testDirectoryCompression() throws IOException {
        Path dirToZip = tempDir.resolve("project_dir");
        Path subDir1 = dirToZip.resolve("src");
        Path subDir2 = dirToZip.resolve("docs/manual");
        Files.createDirectories(subDir1);
        Files.createDirectories(subDir2);

        Files.writeString(dirToZip.resolve("root.txt"), "Root File Content");
        Files.writeString(subDir1.resolve("Main.java"), "public class Main {}");
        Files.writeString(subDir2.resolve("readme.txt"), "Documentation contents");

        // 1. Without root
        Path zipNoRoot = tempDir.resolve("no-root.zip");
        ZipCompressor.compressDirectory(dirToZip, zipNoRoot, false);
        assertTrue(Files.exists(zipNoRoot));

        Path extractNoRoot = tempDir.resolve("extract-no-root");
        ZipCompressor.decompress(zipNoRoot, extractNoRoot, true);
        assertTrue(Files.exists(extractNoRoot.resolve("root.txt")));
        assertTrue(Files.exists(extractNoRoot.resolve("src/Main.java")));
        assertTrue(Files.exists(extractNoRoot.resolve("docs/manual/readme.txt")));

        // 2. With root
        Path zipWithRoot = tempDir.resolve("with-root.zip");
        ZipCompressor.compressDirectory(dirToZip, zipWithRoot, true);
        assertTrue(Files.exists(zipWithRoot));

        Path extractWithRoot = tempDir.resolve("extract-with-root");
        ZipCompressor.decompress(zipWithRoot, extractWithRoot, true);
        assertTrue(Files.exists(extractWithRoot.resolve("project_dir/root.txt")));
        assertTrue(Files.exists(extractWithRoot.resolve("project_dir/src/Main.java")));
    }

    @Test
    @DisplayName("Overwrite false should throw FileAlreadyExistsException")
    void testOverwriteProtection() throws IOException {
        Path sourceFile = tempDir.resolve("note.txt");
        Files.writeString(sourceFile, "Note 1");

        Path zipFile = tempDir.resolve("note.zip");
        ZipCompressor.compressFile(sourceFile, zipFile);

        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("note.txt"), "Existing content");

        assertThrows(FileAlreadyExistsException.class, () ->
                ZipCompressor.decompress(zipFile, targetDir, false)
        );

        // Overwrite true should succeed
        ZipCompressor.decompress(zipFile, targetDir, true);
        assertEquals("Note 1", Files.readString(targetDir.resolve("note.txt")));
    }

    @Test
    @DisplayName("ZipSlip path traversal attempt must be blocked with SecurityException")
    void testZipSlipExploitThrowsSecurityException() throws IOException {
        // Manually craft a malicious zip containing ../outside.txt
        Path maliciousZip = tempDir.resolve("slip.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(maliciousZip))) {
            ZipEntry badEntry = new ZipEntry("../../evil.txt");
            zos.putNextEntry(badEntry);
            zos.write("malicious payload".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        Path targetDir = tempDir.resolve("safe_dir");
        Files.createDirectories(targetDir);

        SecurityException ex = assertThrows(SecurityException.class, () ->
                ZipCompressor.decompress(maliciousZip, targetDir, true)
        );
        assertTrue(ex.getMessage().contains("ZipSlip detected"));
        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
    }

    @Test
    @DisplayName("ZipBomb exceeding max allowed total bytes throws SecurityException")
    void testZipBombTotalBytesLimit() throws IOException {
        Path testFile = tempDir.resolve("bomb_source.txt");
        byte[] payload = new byte[100_000]; // 100 KB
        Files.write(testFile, payload);

        Path zipFile = tempDir.resolve("bomb.zip");
        ZipCompressor.compressFile(testFile, zipFile);

        Path targetDir = tempDir.resolve("bomb_extract");
        // Set maxTotalBytes to 5000 bytes (smaller than uncompressed payload)
        ZipGuardOptions strictOptions = new ZipGuardOptions(100, 5000, 1000);

        SecurityException ex = assertThrows(SecurityException.class, () ->
                ZipCompressor.decompress(zipFile, targetDir, true, strictOptions)
        );
        assertTrue(ex.getMessage().contains("Zip bomb detected"));
    }

    @Test
    @DisplayName("Non-existent files or directories throw appropriate exceptions")
    void testInvalidPaths() {
        Path nonExistent = tempDir.resolve("ghost.txt");
        assertThrows(NoSuchFileException.class, () ->
                ZipCompressor.compressFile(nonExistent, tempDir.resolve("out.zip"))
        );
        assertThrows(NoSuchFileException.class, () ->
                ZipCompressor.compressDirectory(nonExistent, tempDir.resolve("out.zip"), false)
        );
        assertThrows(NoSuchFileException.class, () ->
                ZipCompressor.decompress(nonExistent, tempDir.resolve("out_dir"), true)
        );
    }
}
