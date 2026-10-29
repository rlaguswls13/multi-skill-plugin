package com.msp.sysinfo;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Diagnostic probe for querying JVM memory, CPU cores, operating system attributes,
 * thread runtime statistics, and disk storage metrics.
 */
public final class SystemInfoProbe {

    private static final SystemInfoProbe INSTANCE = new SystemInfoProbe();

    public static SystemInfoProbe getInstance() {
        return INSTANCE;
    }

    /**
     * Captures a snapshot of current JVM heap and non-heap memory usage.
     */
    public static JvmMemorySnapshot getJvmMemorySnapshot() {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();

        long heapUsed = heap.getUsed();
        long heapMax = heap.getMax();
        long heapCommitted = heap.getCommitted();

        long nonHeapUsed = nonHeap.getUsed();
        long nonHeapCommitted = nonHeap.getCommitted();

        double denominator = heapMax > 0 ? heapMax : heapCommitted;
        double ratio = denominator > 0 ? (double) heapUsed / denominator : 0.0;

        return new JvmMemorySnapshot(
                heapUsed,
                heapMax,
                heapCommitted,
                nonHeapUsed,
                nonHeapCommitted,
                ratio
        );
    }

    /**
     * Captures a snapshot of the operating system and CPU environment.
     */
    public static OsSnapshot getOsSnapshot() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        return new OsSnapshot(
                osBean.getAvailableProcessors(),
                osBean.getName(),
                osBean.getArch(),
                osBean.getVersion(),
                osBean.getSystemLoadAverage()
        );
    }

    /**
     * Captures a snapshot of storage capacity for the filesystem enclosing the specified path.
     *
     * @param path target path to inspect
     */
    public static DiskSnapshot getDiskSnapshot(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        File file = path.toFile();
        long total = file.getTotalSpace();
        long usable = file.getUsableSpace();
        long used = Math.max(0L, total - usable);
        double usedPercent = total > 0 ? ((double) used / (double) total) * 100.0 : 0.0;

        return new DiskSnapshot(path, total, usable, used, usedPercent);
    }

    /**
     * Captures a snapshot of current disk storage for the current working directory.
     */
    public static DiskSnapshot getDiskSnapshot() {
        return getDiskSnapshot(Path.of("."));
    }

    /**
     * Captures a snapshot of live and peak JVM threads.
     */
    public static ThreadSnapshot getThreadSnapshot() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        return new ThreadSnapshot(
                threadBean.getThreadCount(),
                threadBean.getPeakThreadCount(),
                threadBean.getDaemonThreadCount(),
                threadBean.getTotalStartedThreadCount()
        );
    }

    /**
     * Checks if JVM heap memory usage has reached or exceeded the specified threshold ratio (e.g. 0.85 for 85%).
     *
     * @param thresholdRatio ratio threshold between 0.0 and 1.0
     * @return true if heap usage ratio >= thresholdRatio
     */
    public static boolean isLowMemory(double thresholdRatio) {
        if (thresholdRatio <= 0.0) {
            return true;
        }
        if (thresholdRatio > 1.0) {
            return false;
        }
        JvmMemorySnapshot snapshot = getJvmMemorySnapshot();
        return snapshot.heapUsedRatio() >= thresholdRatio;
    }

    /**
     * Checks if the disk usage of the specified path has reached or exceeded the specified threshold ratio (e.g. 0.90 for 90% full).
     *
     * @param path           target path
     * @param thresholdRatio ratio threshold between 0.0 and 1.0
     * @return true if disk used ratio >= thresholdRatio
     */
    public static boolean isLowDiskSpace(Path path, double thresholdRatio) {
        if (thresholdRatio <= 0.0) {
            return true;
        }
        if (thresholdRatio > 1.0) {
            return false;
        }
        DiskSnapshot snapshot = getDiskSnapshot(path);
        return snapshot.usedRatio() >= thresholdRatio;
    }

    /**
     * Checks if the disk usage of the current working directory has reached or exceeded the specified threshold ratio.
     */
    public static boolean isLowDiskSpace(double thresholdRatio) {
        return isLowDiskSpace(Path.of("."), thresholdRatio);
    }
}
