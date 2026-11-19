package com.msp.cgroups;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Snapshot of process and container memory metrics.
 *
 * @param version cgroups version detected or host OS fallback
 * @param currentMemoryBytes current memory usage in bytes
 * @param limitMemoryBytes memory limit in bytes, or empty if unlimited
 * @param usagePercentage memory utilization percentage (0.0% to 100.0%), or 0.0 if unlimited
 * @param isUnlimited whether memory is unlimited
 * @param memoryStats detailed memory breakdown (anon, file, slab, etc.)
 */
public record CgroupMemoryMetrics(
        CgroupVersion version,
        long currentMemoryBytes,
        OptionalLong limitMemoryBytes,
        double usagePercentage,
        boolean isUnlimited,
        Map<String, Long> memoryStats
) {
    public CgroupMemoryMetrics {
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(limitMemoryBytes, "limitMemoryBytes cannot be null");
        memoryStats = memoryStats == null ? Map.of() : Map.copyOf(memoryStats);
    }

    public static CgroupMemoryMetrics of(
            CgroupVersion version,
            long currentMemoryBytes,
            OptionalLong limitMemoryBytes,
            Map<String, Long> memoryStats
    ) {
        boolean unlimited = limitMemoryBytes.isEmpty();
        double percentage = 0.0;
        if (!unlimited && limitMemoryBytes.getAsLong() > 0) {
            percentage = Math.min(100.0, ((double) currentMemoryBytes / limitMemoryBytes.getAsLong()) * 100.0);
        }
        return new CgroupMemoryMetrics(version, currentMemoryBytes, limitMemoryBytes, percentage, unlimited, memoryStats);
    }

    public boolean isOomRisk(double thresholdPercentage) {
        if (isUnlimited) {
            return false;
        }
        return usagePercentage >= thresholdPercentage;
    }

    public long currentMemoryMb() {
        return currentMemoryBytes / (1024 * 1024);
    }

    public OptionalLong limitMemoryMb() {
        if (limitMemoryBytes.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(limitMemoryBytes.getAsLong() / (1024 * 1024));
    }
}
