package com.msp.filewatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FileWatcherTest {

    @Test
    @DisplayName("Should detect file creation event with debouncing")
    void testFileCreation(@TempDir Path tempDir) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<FileChangeEvent> events = new CopyOnWriteArrayList<>();

        try (FileWatcher watcher = FileWatchers.watch(tempDir, Duration.ofMillis(100), (path, type) -> {
            events.add(new FileChangeEvent(path, type));
            latch.countDown();
        })) {
            assertTrue(watcher.isRunning());
            Thread.sleep(100); // Wait for watcher to attach

            Path newFile = tempDir.resolve("sample.txt");
            Files.writeString(newFile, "hello world");

            boolean received = latch.await(3, TimeUnit.SECONDS);
            assertTrue(received, "Timed out waiting for file create event");

            assertEquals(1, events.size());
            assertEquals(newFile.toAbsolutePath().normalize(), events.get(0).path());
            assertEquals(FileChangeType.CREATED, events.get(0).type());
        }
    }

    @Test
    @DisplayName("Should consolidate multiple rapid modifications into a single debounced callback")
    void testDebouncedRapidModifications(@TempDir Path tempDir) throws Exception {
        Path targetFile = tempDir.resolve("config.properties");
        Files.writeString(targetFile, "initial=true\n");

        AtomicInteger callCount = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);

        try (FileWatcher watcher = FileWatchers.watch(tempDir, Duration.ofMillis(200), (path, type) -> {
            if (path.equals(targetFile.toAbsolutePath().normalize())) {
                callCount.incrementAndGet();
                latch.countDown();
            }
        })) {
            Thread.sleep(100);

            // Perform 5 rapid writes within 100ms
            for (int i = 0; i < 5; i++) {
                Files.writeString(targetFile, "update=" + i + "\n");
                Thread.sleep(20);
            }

            boolean received = latch.await(3, TimeUnit.SECONDS);
            assertTrue(received, "Timed out waiting for debounced modification event");

            // Wait an extra quiet buffer to verify no additional callbacks fire
            Thread.sleep(350);
            assertEquals(1, callCount.get(), "Debouncing should consolidate burst modifications into 1 event");
        }
    }

    @Test
    @DisplayName("Should detect file deletion event")
    void testFileDeletion(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("delete-me.tmp");
        Files.writeString(file, "temp content");

        CountDownLatch latch = new CountDownLatch(1);
        List<FileChangeEvent> events = new CopyOnWriteArrayList<>();

        try (FileWatcher watcher = FileWatchers.watch(tempDir, Duration.ofMillis(100), (path, type) -> {
            if (path.equals(file.toAbsolutePath().normalize()) && type == FileChangeType.DELETED) {
                events.add(new FileChangeEvent(path, type));
                latch.countDown();
            }
        })) {
            Thread.sleep(100);

            Files.delete(file);

            boolean received = latch.await(3, TimeUnit.SECONDS);
            assertTrue(received, "Timed out waiting for delete event");
            assertEquals(FileChangeType.DELETED, events.get(0).type());
        }
    }

    @Test
    @DisplayName("Should watch recursively and detect changes in subdirectories")
    void testRecursiveWatching(@TempDir Path tempDir) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<FileChangeEvent> events = new CopyOnWriteArrayList<>();

        try (FileWatcher watcher = FileWatchers.watchRecursive(tempDir, Duration.ofMillis(100), (path, type) -> {
            if (path.getFileName().toString().equals("deep.txt")) {
                events.add(new FileChangeEvent(path, type));
                latch.countDown();
            }
        })) {
            Thread.sleep(100);

            Path subDir = Files.createDirectory(tempDir.resolve("nested"));
            Thread.sleep(100); // Allow watcher to dynamically register new subdirectory

            Path nestedFile = subDir.resolve("deep.txt");
            Files.writeString(nestedFile, "nested content");

            boolean received = latch.await(3, TimeUnit.SECONDS);
            assertTrue(received, "Timed out waiting for recursive nested event");
            assertEquals(FileChangeType.CREATED, events.get(0).type());
        }
    }

    @Test
    @DisplayName("Should properly stop watcher and cease firing callbacks")
    void testLifecycleStop(@TempDir Path tempDir) throws Exception {
        AtomicInteger count = new AtomicInteger();

        FileWatcher watcher = FileWatchers.watch(tempDir, Duration.ofMillis(100), (path, type) -> count.incrementAndGet());
        assertTrue(watcher.isRunning());

        watcher.stop();
        assertFalse(watcher.isRunning());

        // File created after stop should not trigger listener
        Files.writeString(tempDir.resolve("after_stop.txt"), "no-op");
        Thread.sleep(250);

        assertEquals(0, count.get(), "Listener should not be called after stop()");
    }
}
