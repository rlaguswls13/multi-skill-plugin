package com.msp.sysinfo;

/**
 * Snapshot of Operating System diagnostic metrics.
 *
 * @param availableProcessors number of logical CPU cores available to the JVM
 * @param osName              operating system name (e.g. Windows 11, Linux)
 * @param arch                operating system architecture (e.g. amd64, aarch64)
 * @param version             operating system version string
 * @param systemLoadAverage   system load average for the last minute, or negative value if unavailable
 */
public record OsSnapshot(
        int availableProcessors,
        String osName,
        String arch,
        String version,
        double systemLoadAverage
) {}
