package com.msp.health;

import java.io.File;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Health check that verifies disk space thresholds on a given filesystem path.
 */
public class DiskSpaceCheck implements HealthCheck {

    private final Path path;
    private final long criticalThresholdBytes;
    private final long warningThresholdBytes;

    public DiskSpaceCheck(Path path, long criticalThresholdBytes, long warningThresholdBytes) {
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.criticalThresholdBytes = criticalThresholdBytes;
        this.warningThresholdBytes = warningThresholdBytes;
    }

    public static DiskSpaceCheck ofBytes(Path path, long criticalBytes, long warningBytes) {
        return new DiskSpaceCheck(path, criticalBytes, warningBytes);
    }

    public static DiskSpaceCheck ofMegabytes(Path path, long criticalMb, long warningMb) {
        long mb = 1024L * 1024L;
        return new DiskSpaceCheck(path, criticalMb * mb, warningMb * mb);
    }

    public static DiskSpaceCheck ofCurrentDirectory(long criticalMb, long warningMb) {
        return ofMegabytes(Path.of("."), criticalMb, warningMb);
    }

    @Override
    public HealthResult check() throws Exception {
        File file = path.toFile();
        long totalSpace = file.getTotalSpace();
        long usableSpace = file.getUsableSpace();

        if (totalSpace <= 0) {
            // Attempt Files.getFileStore as fallback
            try {
                FileStore store = Files.getFileStore(path);
                totalSpace = store.getTotalSpace();
                usableSpace = store.getUsableSpace();
            } catch (Exception ignored) {
            }
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", path.toAbsolutePath().toString());
        details.put("totalBytes", totalSpace);
        details.put("usableBytes", usableSpace);
        double usablePercent = totalSpace > 0 ? ((double) usableSpace / totalSpace) * 100.0 : 0.0;
        details.put("usablePercent", Math.round(usablePercent * 100.0) / 100.0);
        details.put("criticalThresholdBytes", criticalThresholdBytes);
        details.put("warningThresholdBytes", warningThresholdBytes);

        if (usableSpace < criticalThresholdBytes) {
            return HealthResult.down(
                    String.format("Critical disk space: usable %d bytes is below threshold %d bytes",
                            usableSpace, criticalThresholdBytes),
                    details,
                    null
            );
        }

        if (usableSpace < warningThresholdBytes) {
            return HealthResult.degraded(
                    String.format("Low disk space: usable %d bytes is below threshold %d bytes",
                            usableSpace, warningThresholdBytes),
                    details,
                    null
            );
        }

        return HealthResult.up(
                String.format("Disk space healthy: usable %d MB (%.1f%%)", usableSpace / (1024 * 1024), usablePercent),
                details,
                null
        );
    }
}
