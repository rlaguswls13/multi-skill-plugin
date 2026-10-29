# 📦 `skill-memory-leak-detector`

> **순수 Java 21 표준 라이브러리 기반 실시간 컬렉션/맵 메모리 누수 감지기 (단조 증가 분석, 초당 증가 속도, 한계 용량 경보)**

- **아티팩트 좌표**: `com.msp:skill-memory-leak-detector:1.0.0`
- **개별 JAR 산출물**: `modules/skill-memory-leak-detector/build/libs/skill-memory-leak-detector-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **컬렉션, 맵, 커스텀 `IntSupplier` 모니터링**:
   - `watch(name, collection)`, `watch(name, map)`, `watch(name, supplier)`
   - 객체 레퍼런스 침투 없이 크기(`size()`) 추이만 경량 샘플링.
2. **다차원 누수 탐지 규칙 (`DetectionConfig`)**:
   - `MONOTONIC_GROWTH`: N회 연속 샘플링 동안 감소 없이 단조 증가 시 감지 (언바운디드 버퍼/큐 누수).
   - `GROWTH_VELOCITY_EXCEEDED`: 초당 원소 증가 속도(`items/sec`) 임계치 초과 시 경보.
   - `CAPACITY_LIMIT_EXCEEDED`: 절대 한계 용량(`maxCapacity`) 초과 시 즉시 경보.
3. **이벤트 리스너 & 백그라운드 주기적 스케줄링**:
   - `addListener(LeakListener)` 콜백 제공.
   - `start(Duration interval)`: 데몬 스레드 기반 백그라운드 주기 샘플링.
   - `sample()`: 수동 주기 호출 지원.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.leakdetect.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryLeakDetectorExample {
    public static void main(String[] args) throws InterruptedException {
        // 1. 감지기 생성
        try (MemoryLeakDetector detector = new MemoryLeakDetector()) {

            // 2. 누수 경보 리스너 등록
            detector.addListener(warning -> {
                System.err.printf("[ALERT] %s: %s (Velocity: %.2f/s)%n",
                        warning.targetName(), warning.message(), warning.velocityPerSecond());
            });

            // 3. 모니터링 대상 등록
            Map<String, Object> sessionCache = new ConcurrentHashMap<>();
            DetectionConfig config = DetectionConfig.builder()
                    .consecutiveGrowthThreshold(5) // 5연속 증가 시 경보
                    .velocityThresholdPerSecond(50.0) // 초당 50개 이상 증가 시 경보
                    .maxCapacityThreshold(10_000)   // 최대 1만 개 한계
                    .build();

            detector.watch("sessionCache", sessionCache, config);

            // 4. 주기적 감시 시작 (500ms 간격)
            detector.start(Duration.ofMillis(500));

            // 가상 워크로드 시뮬레이션
            for (int i = 0; i < 20; i++) {
                sessionCache.put("token-" + i, new byte[1024]);
                Thread.sleep(100);
            }

            // 5. 실시간 통계 조회
            detector.getStats("sessionCache").ifPresent(stats -> {
                System.out.printf("Current size: %d, Samples: %d, Consecutive increases: %d%n",
                        stats.currentSize(), stats.sampleCount(), stats.consecutiveGrowthCount());
            });
        }
    }
}
```
