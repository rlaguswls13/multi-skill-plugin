package com.msp.health;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check that verifies JVM memory usage against configurable thresholds.
 */
public class FreeMemoryCheck implements HealthCheck {

    private final double warningUsageRatio;   // e.g. 0.85
    private final double criticalUsageRatio;  // e.g. 0.95

    public FreeMemoryCheck(double warningUsageRatio, double criticalUsageRatio) {
        if (warningUsageRatio < 0.0 || warningUsageRatio > 1.0) {
            throw new IllegalArgumentException("warningUsageRatio must be between 0.0 and 1.0");
        }
        if (criticalUsageRatio < 0.0 || criticalUsageRatio > 1.0) {
            throw new IllegalArgumentException("criticalUsageRatio must be between 0.0 and 1.0");
        }
        if (warningUsageRatio > criticalUsageRatio) {
            throw new IllegalArgumentException("warningUsageRatio must be <= criticalUsageRatio");
        }
        this.warningUsageRatio = warningUsageRatio;
        this.criticalUsageRatio = criticalUsageRatio;
    }

    public static FreeMemoryCheck defaults() {
        return new FreeMemoryCheck(0.85, 0.95);
    }

    public static FreeMemoryCheck ofRatios(double warningRatio, double criticalRatio) {
        return new FreeMemoryCheck(warningRatio, criticalRatio);
    }

    @Override
    public HealthResult check() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        // Effective max memory
        long effectiveMax = maxMemory == Long.MAX_VALUE ? totalMemory : maxMemory;
        double usageRatio = effectiveMax > 0 ? (double) usedMemory / effectiveMax : 0.0;
        double usagePercent = Math.round(usageRatio * 10000.0) / 100.0;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("usedBytes", usedMemory);
        details.put("totalBytes", totalMemory);
        details.put("maxBytes", maxMemory);
        details.put("usagePercent", usagePercent);
        details.put("warningRatio", warningUsageRatio);
        details.put("criticalRatio", criticalUsageRatio);

        if (usageRatio >= criticalUsageRatio) {
            return HealthResult.down(
                    String.format("Critical memory pressure: %.1f%% used (exceeds critical %.1f%%)",
                            usagePercent, criticalUsageRatio * 100.0),
                    details,
                    null
            );
        }

        if (usageRatio >= warningUsageRatio) {
            return HealthResult.degraded(
                    String.format("High memory usage: %.1f%% used (exceeds warning %.1f%%)",
                            usagePercent, warningUsageRatio * 100.0),
                    details,
                    null
            );
        }

        return HealthResult.up(
                String.format("Memory healthy: %.1f%% used (%d MB / %d MB)",
                        usagePercent, usedMemory / (1024 * 1024), effectiveMax / (1024 * 1024)),
                details,
                null
        );
    }
}
