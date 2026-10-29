package com.msp.sysinfo;

/**
 * Snapshot of JVM heap and non-heap memory utilization.
 *
 * @param heapUsed        bytes of heap currently used
 * @param heapMax         maximum bytes of heap, or -1 if undefined
 * @param heapCommitted   bytes of heap committed for the JVM
 * @param nonHeapUsed     bytes of non-heap currently used
 * @param nonHeapCommitted bytes of non-heap committed
 * @param heapUsedRatio   ratio of heap used against max (or committed if max is undefined)
 */
public record JvmMemorySnapshot(
        long heapUsed,
        long heapMax,
        long heapCommitted,
        long nonHeapUsed,
        long nonHeapCommitted,
        double heapUsedRatio
) {
    public double heapUsedPercent() {
        return heapUsedRatio * 100.0;
    }

    public boolean isMaxDefined() {
        return heapMax > 0;
    }
}
