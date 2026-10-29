# 📦 `skill-system-info-probe`

> **순수 Java 21 표준 라이브러리 기반 JVM 힙/논힙 메모리, OS/CPU 코어, 디스크 저장 공간, 스레드 런타임 진단 프로브**

- **아티팩트 좌표**: `com.msp:skill-system-info-probe:1.0.0`
- **개별 JAR 산출물**: `modules/skill-system-info-probe/build/libs/skill-system-info-probe-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **JVM 메모리 진단 (`getJvmMemorySnapshot`)**:
   - `heapUsed`, `heapMax`, `heapCommitted`, `nonHeapUsed`, `nonHeapCommitted`, `heapUsedPercent()` 제공.
   - `-Xmx` 미지정(-1) 시 committed 기준 동적 비율 산출.
2. **OS & CPU 환경 (`getOsSnapshot`)**:
   - `availableProcessors`(논리 코어 수), `osName`, `arch`, `version`, `systemLoadAverage`.
3. **디스크 스토리지 용량 진단 (`getDiskSnapshot`)**:
   - 특정 경로(`Path`) 또는 작업 디렉터리의 `totalSpace`, `usableSpace`, `usedSpace`, `usedPercent()`, `freePercent()`.
4. **스레드 상태 진단 (`getThreadSnapshot`)**:
   - `activeCount`, `peakCount`, `daemonCount`, `totalStartedCount`.
5. **임계치 기반 경보 (`isLowMemory`, `isLowDiskSpace`)**:
   - 0.0 ~ 1.0 비율 기준 메모리/디스크 고갈 여부 빠른 불리언 판정.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.sysinfo.*;
import java.nio.file.Path;

public class SystemProbeExample {
    public static void main(String[] args) {
        // 1. JVM 메모리 상태
        JvmMemorySnapshot mem = SystemInfoProbe.getJvmMemorySnapshot();
        System.out.printf("JVM Heap Used: %d MB / %d MB (%.2f%%)%n",
                mem.heapUsed() / (1024 * 1024),
                mem.heapMax() / (1024 * 1024),
                mem.heapUsedPercent());

        // 2. OS 및 코어 정보
        OsSnapshot os = SystemInfoProbe.getOsSnapshot();
        System.out.printf("OS: %s (%s, %s), CPU Cores: %d%n",
                os.osName(), os.arch(), os.version(), os.availableProcessors());

        // 3. 디스크 사용량
        DiskSnapshot disk = SystemInfoProbe.getDiskSnapshot(Path.of("."));
        System.out.printf("Disk: Total %.2f GB, Usable %.2f GB (Used: %.1f%%)%n",
                disk.totalSpace() / 1e9,
                disk.usableSpace() / 1e9,
                disk.usedPercent());

        // 4. 스레드 상태
        ThreadSnapshot threads = SystemInfoProbe.getThreadSnapshot();
        System.out.printf("Threads: Live %d (Peak %d, Daemon %d)%n",
                threads.activeCount(), threads.peakCount(), threads.daemonCount());

        // 5. 메모리/디스크 부족 경고 (예: 90% 이상 사용 시)
        if (SystemInfoProbe.isLowMemory(0.90)) {
            System.err.println("WARNING: JVM Heap memory usage exceeds 90%!");
        }
        if (SystemInfoProbe.isLowDiskSpace(0.90)) {
            System.err.println("WARNING: Disk usage exceeds 90%!");
        }
    }
}
```
