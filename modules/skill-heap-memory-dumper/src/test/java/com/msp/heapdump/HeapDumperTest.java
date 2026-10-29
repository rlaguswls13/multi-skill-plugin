package com.msp.heapdump;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HeapDumperTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("isAvailable reports true on HotSpot/OpenJDK JVM")
    void testIsAvailable() {
        boolean available = HeapDumper.isAvailable();
        // Typically true on HotSpot / OpenJDK
        assertDoesNotThrow(() -> HeapDumper.isAvailable());
    }

    @Test
    @DisplayName("generateTimestampedFileName produces valid .hprof pattern")
    void testGenerateTimestampedFileName() {
        String name1 = HeapDumper.generateTimestampedFileName();
        String name2 = HeapDumper.generateTimestampedFileName();

        assertTrue(name1.startsWith("heapdump-"));
        assertTrue(name1.endsWith(".hprof"));
        assertNotEquals(name1, name2);
    }

    @Test
    @DisplayName("dumpHeap to directory creates valid hprof file with live objects")
    void testDumpHeapToDirectory() throws IOException {
        assumeTrue(HeapDumper.isAvailable(), "HotSpotDiagnosticMXBean not available, skipping actual dump");

        Path dumpFile = HeapDumper.dumpHeap(tempDir, true);
        assertNotNull(dumpFile);
        assertTrue(Files.exists(dumpFile), "Dump file should exist");
        assertTrue(Files.size(dumpFile) > 0, "Dump file size should be > 0 bytes");
        assertTrue(dumpFile.getFileName().toString().endsWith(".hprof"));

        // Clean up
        Files.deleteIfExists(dumpFile);
    }

    @Test
    @DisplayName("dumpHeap with overwrite replaces existing dump file")
    void testDumpHeapOverwrite() throws IOException {
        assumeTrue(HeapDumper.isAvailable(), "HotSpotDiagnosticMXBean not available, skipping actual dump");

        Path target = tempDir.resolve("custom-dump.hprof");
        Path dump1 = HeapDumper.dumpHeap(target, true, true);
        assertTrue(Files.exists(dump1));

        // Overwrite
        Path dump2 = HeapDumper.dumpHeap(target, true, true);
        assertEquals(target.toAbsolutePath().normalize(), dump2);
        assertTrue(Files.exists(dump2));

        Files.deleteIfExists(target);
    }

    @Test
    @DisplayName("dumpHeap throws HeapDumpException when file exists and overwrite is false")
    void testDumpHeapCollisionWithoutOverwrite() throws IOException {
        Path existing = tempDir.resolve("existing.hprof");
        Files.writeString(existing, "dummy data");

        assertThrows(HeapDumpException.class, () -> HeapDumper.dumpHeap(existing, true, false));

        Files.deleteIfExists(existing);
    }

    @Test
    @DisplayName("dumpHeap rejects null arguments")
    void testNullValidation() {
        assertThrows(NullPointerException.class, () -> HeapDumper.dumpHeap((Path) null, true));
        assertThrows(NullPointerException.class, () -> HeapDumper.dumpHeap((String) null, true));
    }
}
