package com.msp.sysinfo;

import java.nio.file.Path;

/**
 * Snapshot of disk storage capacity and usage for a given file system path.
 *
 * @param path        evaluated path
 * @param totalSpace  total storage capacity in bytes
 * @param usableSpace usable free space in bytes for this JVM process
 * @param usedSpace   calculated used bytes (totalSpace - usableSpace)
 * @param usedPercent percentage of used space (0.0 to 100.0)
 */
public record DiskSnapshot(
        Path path,
        long totalSpace,
        long usableSpace,
        long usedSpace,
        double usedPercent
) {
    public double usedRatio() {
        return usedPercent / 100.0;
    }

    public double freePercent() {
        return Math.max(0.0, 100.0 - usedPercent);
    }
}
