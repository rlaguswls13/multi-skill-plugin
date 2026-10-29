# 📦 `skill-heap-memory-dumper`

> **순수 Java 21 표준 JMX / HotSpotDiagnostic 기반 JVM 힙 메모리 덤프(`.hprof`) 생성기**

- **아티팩트 좌표**: `com.msp:skill-heap-memory-dumper:1.0.0`
- **개별 JAR 산출물**: `modules/skill-heap-memory-dumper/build/libs/skill-heap-memory-dumper-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **표준 JMX Platform MBeanServer & HotSpot 리플렉션 폴백**:
   - `com.sun.management:type=HotSpotDiagnostic` MBean 또는 플랫폼 MXBean을 통한 안전한 JVM 힙 덤프 트리거.
   - 외부 프로파일러 에이전트 없이 런타임 코드 내에서 즉시 덤프 생성.
2. **라이브 객체(`liveOnly`) 필터링**:
   - `liveOnly = true`: 덤프 직전 Full GC를 유도하여 가비지 컬렉션되지 않은 실제 활성 메모리 누수 객체만 덤프 (용량 최소화).
   - `liveOnly = false`: 죽은 객체를 포함한 전체 힙 스냅샷 보존.
3. **타임스탬프 기반 자동 파일명 생성 (`generateTimestampedFileName`)**:
   - `heapdump-yyyyMMdd-HHmmss-SSS-seq.hprof` 네이밍 템플릿으로 출력 디렉터리 내 중복 방지.
4. **출력 디렉터리 자동 생성 및 덮어쓰기(`overwrite`) 정책 제어**.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.heapdump.HeapDumper;
import java.nio.file.Path;

public class HeapDumpExample {
    public static void main(String[] args) {
        // 1. 지원 여부 점검
        if (!HeapDumper.isAvailable()) {
            System.err.println("HotSpotDiagnostic is not supported on this JVM.");
            return;
        }

        // 2. 디렉터리에 타임스탬프 파일로 라이브 힙 덤프 생성 (권장)
        Path dumpDir = Path.of("./dumps");
        Path dumpFile = HeapDumper.dumpHeap(dumpDir, true);
        System.out.println("Heap dump saved to: " + dumpFile);

        // 3. 특정 경로에 직접 지정하여 덤프
        Path customFile = HeapDumper.dumpHeap("./logs/oom-snapshot.hprof", true);
        System.out.println("Custom snapshot: " + customFile);
    }
}
```
