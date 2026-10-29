package com.msp.tempfile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempFileManagerTest {

    @TempDir
    Path testDir;

    @AfterEach
    void tearDown() {
        TempFileManager.cleanupAll();
    }

    @Test
    @DisplayName("TempFile auto-deletes on try-with-resources completion")
    void testTempFileAutoDelete() throws IOException {
        Path filePath;
        try (TempFile tempFile = TempFileManager.createTempFile("test-prefix-", ".txt")) {
            filePath = tempFile.path();
            assertTrue(Files.exists(filePath));
            assertTrue(tempFile.exists());
            assertFalse(tempFile.isClosed());
            Files.writeString(filePath, "Temp Data");
        }

        assertFalse(Files.exists(filePath));
    }

    @Test
    @DisplayName("TempDirectory recursively deletes nested files and subdirectories on close")
    void testTempDirectoryRecursiveDelete() throws IOException {
        Path dirPath;
        Path nestedFile;
        Path subDir;
        Path deepFile;

        try (TempDirectory tempDir = TempFileManager.createTempDirectory("test-dir-")) {
            dirPath = tempDir.path();
            assertTrue(Files.exists(dirPath));

            nestedFile = tempDir.createFile("child.txt");
            Files.writeString(nestedFile, "Child content");
            assertTrue(Files.exists(nestedFile));

            subDir = tempDir.createSubdirectory("level1/level2");
            assertTrue(Files.exists(subDir));

            deepFile = subDir.resolve("deep.txt");
            Files.writeString(deepFile, "Deep content");
            assertTrue(Files.exists(deepFile));
        }

        assertFalse(Files.exists(dirPath));
        assertFalse(Files.exists(nestedFile));
        assertFalse(Files.exists(subDir));
        assertFalse(Files.exists(deepFile));
    }

    @Test
    @DisplayName("Graceful handling when file is already manually deleted")
    void testAlreadyManuallyDeletedFile() throws IOException {
        Path filePath;
        try (TempFile tempFile = TempFileManager.createTempFile("manual-", ".tmp")) {
            filePath = tempFile.path();
            assertTrue(Files.exists(filePath));
            Files.delete(filePath); // Manually deleted before close
            assertFalse(Files.exists(filePath));
        } // close() should not throw any exception

        assertFalse(Files.exists(filePath));
    }

    @Test
    @DisplayName("deleteOnClose flag can preserve temporary file if explicitly disabled")
    void testPreserveTempFile() throws IOException {
        Path filePath;
        try (TempFile tempFile = TempFileManager.createTempFile("preserve-", ".log")) {
            filePath = tempFile.path();
            tempFile.setDeleteOnClose(false);
            Files.writeString(filePath, "Important log to keep");
        }

        assertTrue(Files.exists(filePath));
        // Manual cleanup
        Files.deleteIfExists(filePath);
    }

    @Test
    @DisplayName("register and cleanupAll deletes all tracked resources")
    void testRegisterAndCleanupAll() throws IOException {
        Path f1 = Files.createFile(testDir.resolve("f1.txt"));
        Path dir1 = Files.createDirectory(testDir.resolve("sub_dir"));
        Files.createFile(dir1.resolve("inner.txt"));

        TempFileManager.register(f1);
        TempFileManager.register(dir1);

        assertTrue(TempFileManager.activeResourceCount() >= 2);
        assertTrue(Files.exists(f1));
        assertTrue(Files.exists(dir1));

        TempFileManager.cleanupAll();

        assertFalse(Files.exists(f1));
        assertFalse(Files.exists(dir1));
        assertEquals(0, TempFileManager.activeResourceCount());
    }

    @Test
    @DisplayName("Active resource count decrements when resource is closed individually")
    void testActiveResourceCount() throws IOException {
        int initial = TempFileManager.activeResourceCount();
        TempFile tf = TempFileManager.createTempFile("cnt-", ".tmp");
        assertEquals(initial + 1, TempFileManager.activeResourceCount());

        tf.close();
        assertEquals(initial, TempFileManager.activeResourceCount());
    }
}
