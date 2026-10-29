package com.msp.dirclean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DirectoryCleanerTest {

    @Test
    @DisplayName("Should delete files older than cutoff and preserve newer files")
    void shouldDeleteFilesOlderThanCutoff(@TempDir Path tempDir) throws IOException {
        Path oldFile = tempDir.resolve("old.log");
        Path newFile = tempDir.resolve("new.log");

        Files.writeString(oldFile, "Old log content with some bytes");
        Files.writeString(newFile, "New log content");

        // Set oldFile to 10 days ago
        Files.setLastModifiedTime(oldFile, FileTime.from(Instant.now().minus(Duration.ofDays(10))));
        // newFile stays current

        CleanOptions options = CleanOptions.builder()
                .olderThan(Duration.ofDays(2))
                .globPattern("*.log")
                .build();

        CleanReport report = DirectoryCleaner.clean(tempDir, options);

        assertEquals(1, report.deletedFileCount());
        assertFalse(Files.exists(oldFile), "Old file should have been deleted");
        assertTrue(Files.exists(newFile), "New file should be preserved");
        assertTrue(report.freedBytes() > 0);
    }

    @Test
    @DisplayName("Should not delete any files on disk when dryRun is enabled")
    void shouldSimulateDeletionInDryRun(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("dump1.tmp");
        Path file2 = tempDir.resolve("dump2.tmp");
        Files.writeString(file1, "Content 1");
        Files.writeString(file2, "Content 2");

        CleanOptions options = CleanOptions.builder()
                .globPattern("*.tmp")
                .dryRun(true)
                .build();

        CleanReport report = DirectoryCleaner.clean(tempDir, options);

        assertEquals(2, report.deletedFileCount());
        assertEquals(file1.toFile().length() + file2.toFile().length(), report.freedBytes());
        assertTrue(Files.exists(file1), "File 1 must remain on disk in dry-run");
        assertTrue(Files.exists(file2), "File 2 must remain on disk in dry-run");
    }

    @Test
    @DisplayName("Should filter files by glob pattern")
    void shouldFilterByGlobPattern(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("app.log");
        Path txtFile = tempDir.resolve("readme.txt");
        Files.writeString(logFile, "Log data");
        Files.writeString(txtFile, "Important readme");

        CleanOptions options = CleanOptions.builder()
                .globPattern("*.log")
                .build();

        CleanReport report = DirectoryCleaner.clean(tempDir, options);

        assertEquals(1, report.deletedFileCount());
        assertFalse(Files.exists(logFile));
        assertTrue(Files.exists(txtFile));
    }

    @Test
    @DisplayName("Should remove empty directories when deleteEmptyDirs is true")
    void shouldDeleteEmptyDirectories(@TempDir Path tempDir) throws IOException {
        Path subDir = Files.createDirectory(tempDir.resolve("subdir"));
        Path nestedEmpty = Files.createDirectory(subDir.resolve("nested"));
        Path tempFile = nestedEmpty.resolve("temp.cache");
        Files.writeString(tempFile, "cache");

        Path nonEmptydir = Files.createDirectory(tempDir.resolve("preservedDir"));
        Files.writeString(nonEmptydir.resolve("keep.txt"), "keep");

        CleanOptions options = CleanOptions.builder()
                .globPattern("*.cache")
                .deleteEmptyDirs(true)
                .build();

        CleanReport report = DirectoryCleaner.clean(tempDir, options);

        assertEquals(1, report.deletedFileCount());
        assertFalse(Files.exists(tempFile));
        assertFalse(Files.exists(nestedEmpty), "Nested dir became empty and should be deleted");
        assertFalse(Files.exists(subDir), "SubDir became empty and should be deleted");
        assertTrue(Files.exists(nonEmptydir), "Preserved dir has keep.txt so it must remain");
    }

    @Test
    @DisplayName("Should report empty dirs in dryRun without deleting them")
    void shouldSimulateEmptyDirDeletionInDryRun(@TempDir Path tempDir) throws IOException {
        Path subDir = Files.createDirectory(tempDir.resolve("emptydir"));
        Path tempFile = subDir.resolve("temp.log");
        Files.writeString(tempFile, "temp log");

        CleanOptions options = CleanOptions.builder()
                .globPattern("*.log")
                .deleteEmptyDirs(true)
                .dryRun(true)
                .build();

        CleanReport report = DirectoryCleaner.clean(tempDir, options);

        assertEquals(1, report.deletedFileCount());
        assertEquals(1, report.deletedDirCount());
        assertTrue(Files.exists(subDir), "Dir must remain on disk in dryRun");
        assertTrue(Files.exists(tempFile), "File must remain on disk in dryRun");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException if root is invalid")
    void shouldThrowIfRootInvalid(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("does-not-exist");
        assertThrows(IllegalArgumentException.class, () -> DirectoryCleaner.clean(nonExistent));
    }
}
