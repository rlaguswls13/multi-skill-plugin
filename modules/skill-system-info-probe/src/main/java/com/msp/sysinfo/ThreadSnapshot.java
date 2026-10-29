package com.msp.sysinfo;

/**
 * Snapshot of JVM thread diagnostic metrics.
 *
 * @param activeCount       current number of live threads (daemon and non-daemon)
 * @param peakCount         peak live thread count since JVM startup or peak reset
 * @param daemonCount       current number of live daemon threads
 * @param totalStartedCount total number of threads created and started since JVM startup
 */
public record ThreadSnapshot(
        int activeCount,
        int peakCount,
        int daemonCount,
        long totalStartedCount
) {}
