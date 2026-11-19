package com.msp.cgroups;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CgroupMemoryMonitorTest {

    @Test
    @DisplayName("Simulate cgroups v2 container memory inspection")
    void testCgroupsV2() {
        MockCgroupFsProvider fs = new MockCgroupFsProvider()
                .withFile("/sys/fs/cgroup/memory.current", "524288000\n") // 500 MB
                .withFile("/sys/fs/cgroup/memory.max", "1048576000\n")     // 1000 MB
                .withFile("/sys/fs/cgroup/memory.stat", """
                        anon 209715200
                        file 314572800
                        kernel_stack 40960
                        slab 81920
                        """);

        CgroupMemoryMonitor monitor = CgroupMemoryMonitor.withFsProvider(fs);
        CgroupMemoryMetrics metrics = monitor.probe();

        assertEquals(CgroupVersion.V2, metrics.version());
        assertEquals(524288000L, metrics.currentMemoryBytes());
        assertEquals(500, metrics.currentMemoryMb());
        assertTrue(metrics.limitMemoryBytes().isPresent());
        assertEquals(1048576000L, metrics.limitMemoryBytes().getAsLong());
        assertEquals(1000, metrics.limitMemoryMb().getAsLong());
        assertFalse(metrics.isUnlimited());
        assertEquals(50.0, metrics.usagePercentage(), 0.01);

        assertFalse(metrics.isOomRisk(80.0));
        assertTrue(metrics.isOomRisk(45.0));

        assertEquals(209715200L, metrics.memoryStats().get("anon"));
        assertEquals(314572800L, metrics.memoryStats().get("file"));
    }

    @Test
    @DisplayName("Simulate cgroups v2 with unlimited memory (max)")
    void testCgroupsV2Unlimited() {
        MockCgroupFsProvider fs = new MockCgroupFsProvider()
                .withFile("/sys/fs/cgroup/memory.current", "262144000\n")
                .withFile("/sys/fs/cgroup/memory.max", "max\n");

        CgroupMemoryMonitor monitor = CgroupMemoryMonitor.withFsProvider(fs);
        CgroupMemoryMetrics metrics = monitor.probe();

        assertEquals(CgroupVersion.V2, metrics.version());
        assertTrue(metrics.isUnlimited());
        assertTrue(metrics.limitMemoryBytes().isEmpty());
        assertFalse(metrics.isOomRisk(90.0));
    }

    @Test
    @DisplayName("Simulate cgroups v1 container memory inspection")
    void testCgroupsV1() {
        MockCgroupFsProvider fs = new MockCgroupFsProvider()
                .withFile("/sys/fs/cgroup/memory/memory.usage_in_bytes", "800000000\n")
                .withFile("/sys/fs/cgroup/memory/memory.limit_in_bytes", "1000000000\n")
                .withFile("/sys/fs/cgroup/memory/memory.stat", "rss 500000000\ncache 300000000\n");

        CgroupMemoryMonitor monitor = CgroupMemoryMonitor.withFsProvider(fs);
        CgroupMemoryMetrics metrics = monitor.probe();

        assertEquals(CgroupVersion.V1, metrics.version());
        assertEquals(800000000L, metrics.currentMemoryBytes());
        assertTrue(metrics.limitMemoryBytes().isPresent());
        assertEquals(1000000000L, metrics.limitMemoryBytes().getAsLong());
        assertEquals(80.0, metrics.usagePercentage(), 0.01);
        assertTrue(metrics.isOomRisk(75.0));
        assertFalse(metrics.isOomRisk(85.0));
        assertEquals(500000000L, metrics.memoryStats().get("rss"));
    }

    @Test
    @DisplayName("Simulate cgroups v1 with kernel unlimited value")
    void testCgroupsV1Unlimited() {
        MockCgroupFsProvider fs = new MockCgroupFsProvider()
                .withFile("/sys/fs/cgroup/memory/memory.usage_in_bytes", "100000000\n")
                .withFile("/sys/fs/cgroup/memory/memory.limit_in_bytes", "9223372036854771712\n");

        CgroupMemoryMonitor monitor = CgroupMemoryMonitor.withFsProvider(fs);
        CgroupMemoryMetrics metrics = monitor.probe();

        assertEquals(CgroupVersion.V1, metrics.version());
        assertTrue(metrics.isUnlimited());
        assertTrue(metrics.limitMemoryBytes().isEmpty());
    }

    @Test
    @DisplayName("Host OS fallback when cgroup paths do not exist")
    void testHostFallback() {
        MockCgroupFsProvider emptyFs = new MockCgroupFsProvider();
        CgroupMemoryMonitor monitor = CgroupMemoryMonitor.withFsProvider(emptyFs);
        CgroupMemoryMetrics metrics = monitor.probe();

        assertEquals(CgroupVersion.HOST_FALLBACK, metrics.version());
        assertTrue(metrics.currentMemoryBytes() > 0);
        assertTrue(metrics.limitMemoryBytes().isPresent());
        assertTrue(metrics.usagePercentage() >= 0.0 && metrics.usagePercentage() <= 100.0);
    }

    @Test
    @DisplayName("Live host probe executes safely on current runtime")
    void testLiveHostProbe() {
        CgroupMemoryMonitor monitor = CgroupMemoryMonitor.create();
        CgroupMemoryMetrics metrics = monitor.probe();

        assertNotNull(metrics.version());
        assertTrue(metrics.currentMemoryBytes() > 0);
    }
}
