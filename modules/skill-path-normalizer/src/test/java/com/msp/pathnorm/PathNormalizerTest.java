package com.msp.pathnorm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PathNormalizerTest {

    @Test
    @DisplayName("Should normalize separators and resolve relative dot segments")
    void shouldNormalizePath() {
        assertEquals("a/b/c", PathNormalizer.normalize("a\\b\\c"));
        assertEquals("a/c/d", PathNormalizer.normalize("a/b/../c/./d"));
        assertEquals("a/b/c", PathNormalizer.normalize("a///b//c"));
        assertEquals("/c", PathNormalizer.normalize("/a/b/../../c"));
        assertEquals("C:/foo/bar", PathNormalizer.normalize("C:\\foo\\sub\\..\\bar"));
        assertEquals("", PathNormalizer.normalize(""));
        assertEquals(".", PathNormalizer.normalize("././."));
    }

    @Test
    @DisplayName("Should successfully safely resolve harmless relative subpaths")
    void shouldSafeResolveHarmlessSubpaths(@TempDir Path tempDir) {
        Path resolved = PathNormalizer.safeResolve(tempDir, "uploads/avatar.png");
        assertEquals(tempDir.resolve("uploads/avatar.png").normalize(), resolved);

        Path nestedWithRelative = PathNormalizer.safeResolve(tempDir, "sub/temp/../final.txt");
        assertEquals(tempDir.resolve("sub/final.txt").normalize(), nestedWithRelative);

        assertTrue(PathNormalizer.isSafePath(tempDir, "documents/report.pdf"));
    }

    @Test
    @DisplayName("Should detect and throw SecurityException on path traversal attacks")
    void shouldThrowOnTraversalAttacks(@TempDir Path tempDir) {
        assertThrows(SecurityException.class, () ->
                PathNormalizer.safeResolve(tempDir, "../../etc/passwd"));

        assertThrows(SecurityException.class, () ->
                PathNormalizer.safeResolve(tempDir, "uploads/../../secret.key"));

        assertFalse(PathNormalizer.isSafePath(tempDir, "../../../windows/win.ini"));
    }

    @Test
    @DisplayName("Should block URL-encoded and double-encoded traversal attacks")
    void shouldBlockUrlEncodedTraversal(@TempDir Path tempDir) {
        // %2e%2e%2f is ../
        assertThrows(SecurityException.class, () ->
                PathNormalizer.safeResolve(tempDir, "%2e%2e%2f%2e%2e%2fetc%2fshadow"));

        // Double encoded: %252e%252e
        assertThrows(SecurityException.class, () ->
                PathNormalizer.safeResolve(tempDir, "%252e%252e%252fsecret.txt"));
    }

    @Test
    @DisplayName("Should block absolute paths and null-byte poisoning")
    void shouldBlockAbsolutePathsAndNullBytes(@TempDir Path tempDir) {
        assertThrows(SecurityException.class, () ->
                PathNormalizer.safeResolve(tempDir, "/etc/passwd"));

        assertThrows(SecurityException.class, () ->
                PathNormalizer.safeResolve(tempDir, "test.txt\0.exe"));

        assertThrows(SecurityException.class, () ->
                PathNormalizer.safeResolve(tempDir, "test.txt%00.exe"));
    }

    @Test
    @DisplayName("Should sanitize file names removing illegal characters")
    void shouldSanitizeFileName() {
        assertEquals("filenamewithillegalcharsherebar",
                PathNormalizer.sanitizeFileName("file:name*with?illegal\"chars<here>|bar\0"));

        assertEquals("my-document.pdf",
                PathNormalizer.sanitizeFileName("   ...my-document.pdf...  "));

        assertEquals("unnamed",
                PathNormalizer.sanitizeFileName("   :::***???   "));

        assertEquals("unnamed",
                PathNormalizer.sanitizeFileName(null));
    }

    @Test
    @DisplayName("Should protect against Windows reserved device names")
    void shouldProtectAgainstReservedDeviceNames() {
        assertEquals("_CON.txt", PathNormalizer.sanitizeFileName("CON.txt"));
        assertEquals("_con.txt", PathNormalizer.sanitizeFileName("con.txt"));
        assertEquals("_NUL", PathNormalizer.sanitizeFileName("NUL"));
        assertEquals("_COM1.log", PathNormalizer.sanitizeFileName("COM1.log"));
    }
}
