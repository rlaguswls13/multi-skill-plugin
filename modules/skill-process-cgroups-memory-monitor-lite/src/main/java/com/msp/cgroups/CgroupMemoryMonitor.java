package com.msp.cgroups;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Probes process and container memory metrics supporting Linux cgroups v1/v2 with host OS fallback.
 */
public class CgroupMemoryMonitor {

    // cgroups v2 paths
    public static final String CGROUP_V2_CURRENT = "/sys/fs/cgroup/memory.current";
    public static final String CGROUP_V2_MAX = "/sys/fs/cgroup/memory.max";
    public static final String CGROUP_V2_STAT = "/sys/fs/cgroup/memory.stat";

    // cgroups v1 paths
    public static final String CGROUP_V1_USAGE = "/sys/fs/cgroup/memory/memory.usage_in_bytes";
    public static final String CGROUP_V1_LIMIT = "/sys/fs/cgroup/memory/memory.limit_in_bytes";
    public static final String CGROUP_V1_STAT = "/sys/fs/cgroup/memory/memory.stat";

    private static final long UNLIMITED_THRESHOLD = 0x7FFFFFFFFFFFF000L; // Linux unlimited threshold

    private final CgroupFsProvider fsProvider;

    public CgroupMemoryMonitor(CgroupFsProvider fsProvider) {
        this.fsProvider = Objects.requireNonNull(fsProvider, "fsProvider cannot be null");
    }

    public static CgroupMemoryMonitor create() {
        return new CgroupMemoryMonitor(new DefaultCgroupFsProvider());
    }

    public static CgroupMemoryMonitor withFsProvider(CgroupFsProvider fsProvider) {
        return new CgroupMemoryMonitor(fsProvider);
    }

    public CgroupMemoryMetrics probe() {
        if (fsProvider.exists(CGROUP_V2_CURRENT)) {
            return probeV2();
        } else if (fsProvider.exists(CGROUP_V1_USAGE)) {
            return probeV1();
        } else {
            return probeHostFallback();
        }
    }

    private CgroupMemoryMetrics probeV2() {
        try {
            long current = Long.parseLong(fsProvider.readString(CGROUP_V2_CURRENT));
            OptionalLong limit = OptionalLong.empty();

            if (fsProvider.exists(CGROUP_V2_MAX)) {
                String maxStr = fsProvider.readString(CGROUP_V2_MAX);
                if (!"max".equalsIgnoreCase(maxStr)) {
                    try {
                        long maxVal = Long.parseLong(maxStr);
                        if (maxVal < UNLIMITED_THRESHOLD && maxVal > 0) {
                            limit = OptionalLong.of(maxVal);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            Map<String, Long> stats = parseStatFile(CGROUP_V2_STAT);
            return CgroupMemoryMetrics.of(CgroupVersion.V2, current, limit, stats);
        } catch (Exception e) {
            return probeHostFallback();
        }
    }

    private CgroupMemoryMetrics probeV1() {
        try {
            long current = Long.parseLong(fsProvider.readString(CGROUP_V1_USAGE));
            OptionalLong limit = OptionalLong.empty();

            if (fsProvider.exists(CGROUP_V1_LIMIT)) {
                try {
                    long limitVal = Long.parseLong(fsProvider.readString(CGROUP_V1_LIMIT));
                    if (limitVal < UNLIMITED_THRESHOLD && limitVal > 0) {
                        limit = OptionalLong.of(limitVal);
                    }
                } catch (NumberFormatException ignored) {}
            }

            Map<String, Long> stats = parseStatFile(CGROUP_V1_STAT);
            return CgroupMemoryMetrics.of(CgroupVersion.V1, current, limit, stats);
        } catch (Exception e) {
            return probeHostFallback();
        }
    }

    private CgroupMemoryMetrics probeHostFallback() {
        long currentBytes = 0;
        OptionalLong limitBytes = OptionalLong.empty();
        Map<String, Long> stats = new HashMap<>();

        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            Long total = invokeOsBeanMethod(osBean, "getTotalMemorySize", "getTotalPhysicalMemorySize");
            Long free = invokeOsBeanMethod(osBean, "getFreeMemorySize", "getFreePhysicalMemorySize");

            if (total != null && total > 0 && free != null && free >= 0) {
                currentBytes = total - free;
                limitBytes = OptionalLong.of(total);
                stats.put("host_total_bytes", total);
                stats.put("host_free_bytes", free);
                stats.put("host_used_bytes", currentBytes);
            }
        } catch (Exception ignored) {}

        if (limitBytes.isEmpty()) {
            Runtime rt = Runtime.getRuntime();
            currentBytes = rt.totalMemory() - rt.freeMemory();
            limitBytes = OptionalLong.of(rt.maxMemory());
            stats.put("jvm_heap_total", rt.totalMemory());
            stats.put("jvm_heap_free", rt.freeMemory());
            stats.put("jvm_heap_used", currentBytes);
            stats.put("jvm_heap_max", rt.maxMemory());
        }

        return CgroupMemoryMetrics.of(CgroupVersion.HOST_FALLBACK, currentBytes, limitBytes, stats);
    }

    private Map<String, Long> parseStatFile(String path) {
        Map<String, Long> map = new HashMap<>();
        if (!fsProvider.exists(path)) {
            return map;
        }
        try {
            List<String> lines = fsProvider.readLines(path);
            for (String line : lines) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\s+", 2);
                if (parts.length == 2) {
                    try {
                        map.put(parts[0], Long.parseLong(parts[1]));
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException ignored) {}
        return map;
    }

    private Long invokeOsBeanMethod(OperatingSystemMXBean bean, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = bean.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object val = method.invoke(bean);
                if (val instanceof Number n) {
                    return n.longValue();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
