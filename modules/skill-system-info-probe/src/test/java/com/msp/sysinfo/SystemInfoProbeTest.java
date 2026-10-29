package com.msp.sysinfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemInfoProbeTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("JVM Memory snapshot returns valid non-negative metrics")
    void testJvmMemorySnapshot() {
        JvmMemorySnapshot mem = SystemInfoProbe.getJvmMemorySnapshot();
        assertNotNull(mem);
        assertTrue(mem.heapUsed() >= 0);
        assertTrue(mem.heapCommitted() > 0);
        assertTrue(mem.nonHeapUsed() >= 0);
        assertTrue(mem.heapUsedRatio() >= 0.0 && mem.heapUsedRatio() <= 1.0);
        assertTrue(mem.heapUsedPercent() >= 0.0 && mem.heapUsedPercent() <= 100.0);
    }

    @Test
    @DisplayName("Operating System snapshot captures cores and OS identity")
    void testOsSnapshot() {
        OsSnapshot os = SystemInfoProbe.getOsSnapshot();
        assertNotNull(os);
        assertTrue(os.availableProcessors() >= 1);
        assertNotNull(os.osName());
        assertFalse(os.osName().isBlank());
        assertNotNull(os.arch());
        assertFalse(os.arch().isBlank());
    }

    @Test
    @DisplayName("Disk storage snapshot calculates capacity and usage correctly")
    void testDiskSnapshot() {
        DiskSnapshot disk = SystemInfoProbe.getDiskSnapshot(tempDir);
        assertNotNull(disk);
        assertEquals(tempDir, disk.path());
        assertTrue(disk.totalSpace() > 0);
        assertTrue(disk.usableSpace() >= 0);
        assertTrue(disk.usedSpace() >= 0);
        assertTrue(disk.usedPercent() >= 0.0 && disk.usedPercent() <= 100.0);
        assertTrue(disk.freePercent() >= 0.0 && disk.freePercent() <= 100.0);
        assertEquals(100.0, disk.usedPercent() + disk.freePercent(), 0.01);
    }

    @Test
    @DisplayName("Thread snapshot metrics are coherent and positive")
    void testThreadSnapshot() {
        ThreadSnapshot threads = SystemInfoProbe.getThreadSnapshot();
        assertNotNull(threads);
        assertTrue(threads.activeCount() > 0);
        assertTrue(threads.peakCount() >= threads.activeCount());
        assertTrue(threads.daemonCount() >= 0);
        assertTrue(threads.totalStartedCount() >= threads.peakCount());
    }

    @Test
    @DisplayName("Low memory threshold evaluation logic")
    void testIsLowMemory() {
        // 0.0 threshold is always exceeded
        assertTrue(SystemInfoProbe.isLowMemory(0.0));
        // > 1.0 threshold should never be exceeded
        assertFalse(SystemInfoProbe.isLowMemory(1.1));
    }

    @Test
    @DisplayName("Low disk space threshold evaluation logic")
    void testIsLowDiskSpace() {
        assertTrue(SystemInfoProbe.isLowDiskSpace(tempDir, 0.0));
        assertFalse(SystemInfoProbe.isLowDiskSpace(tempDir, 1.1));
        // Default current directory check
        assertTrue(SystemInfoProbe.isLowDiskSpace(0.0));
        assertFalse(SystemInfoProbe.isLowDiskSpace(1.1));
    }

    @Test
    @DisplayName("Singleton instance access")
    void testSingletonInstance() {
        SystemInfoProbe probe = SystemInfoProbe.getInstance();
        assertNotNull(probe);
    }
}
