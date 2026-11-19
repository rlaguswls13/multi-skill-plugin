# skill-process-cgroups-memory-monitor-lite

OS Process & Container Memory Metric Probe (Linux cgroups v1/v2 & Windows OS fallback) in pure Java 21.

## 🚀 Features

- **Linux cgroups v2 Support**:
  - Reads current memory from `/sys/fs/cgroup/memory.current`.
  - Reads memory limit from `/sys/fs/cgroup/memory.max` (handles `"max"` as unlimited).
  - Detailed breakdown from `/sys/fs/cgroup/memory.stat` (`anon`, `file`, `slab`, `kernel_stack`, etc.).
- **Linux cgroups v1 Support**:
  - Reads usage from `/sys/fs/cgroup/memory/memory.usage_in_bytes`.
  - Reads limit from `/sys/fs/cgroup/memory/memory.limit_in_bytes`.
  - Handles kernel unlimited values (e.g. `9223372036854771712`).
- **Host OS Fallback (Windows / macOS / Bare-metal JVM)**:
  - Automatically queries physical OS memory and JVM runtime memory if cgroups are not mounted.
- **OOM Risk Calculation**:
  - Calculates memory usage percentage (`0.0%` to `100.0%`).
  - `isOomRisk(thresholdPercentage)` alerts when memory crosses warning levels (e.g. 85%).
- **Mockable Filesystem Provider**:
  - `CgroupFsProvider` interface with `MockCgroupFsProvider` enables 100% test coverage across OS environments without needing Linux container mounts.
- **Zero Runtime Dependencies**: Pure Java 21 standard library.

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation project(':modules:skill-process-cgroups-memory-monitor-lite')
}
```

## 💡 Quick Start

```java
import com.msp.cgroups.CgroupMemoryMonitor;
import com.msp.cgroups.CgroupMemoryMetrics;

public class Example {
    public static void main(String[] args) {
        CgroupMemoryMonitor monitor = CgroupMemoryMonitor.create();
        CgroupMemoryMetrics metrics = monitor.probe();

        System.out.println("Cgroups Version: " + metrics.version());
        System.out.println("Used Memory: " + metrics.currentMemoryMb() + " MB");
        metrics.limitMemoryMb().ifPresent(limit -> System.out.println("Limit: " + limit + " MB"));
        System.out.printf("Usage: %.1f%%\n", metrics.usagePercentage());

        if (metrics.isOomRisk(85.0)) {
            System.err.println("WARNING: High OOM risk detected!");
        }
    }
}
```
