# 📦 `skill-health-checker`

> **순수 Java 21 표준 라이브러리 기반 복합 헬스 체커 (디스크, 메모리, 핑, 타임아웃 격리, JSON 리포트)**

- **아티팩트 좌표**: `com.msp:skill-health-checker:1.0.0`
- **개별 JAR 산출물**: `modules/skill-health-checker/build/libs/skill-health-checker-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **표준 헬스 상태 모델 (`HealthStatus`)**:
   - `UP`, `DEGRADED`, `UNKNOWN`, `DOWN` 4단계 심각도(`severity`) 모델.
   - 복합 헬스 상태(`combine`): DOWN > DEGRADED > UNKNOWN > UP 우선순위 집계.
2. **내장 컴포넌트 진단 (`HealthCheck`)**:
   - `DiskSpaceCheck`: 지정 경로(또는 현재 작업 디렉터리)의 여유 용량 및 가용 퍼센트 검사.
   - `FreeMemoryCheck`: JVM 힙/메모리 사용률(warning/critical threshold) 검사.
   - `PingCheck`: TCP 소켓 또는 HTTP 엔드포인트 응답 코드/지연시간 진단.
   - Custom `HealthCheck.of(...)`: 람다 및 `Supplier<HealthStatus>` 등록 지원.
3. **가상 스레드 기반 병렬 실행 & 타임아웃 격리**:
   - 각 컴포넌트별 독립 실행 타임아웃(`Duration`) 지정. 지연/블로킹 발생 시 안전하게 취소하고 `DOWN` 상태로 격리.
4. **상세 리포트 및 무의존성 JSON 직렬화 (`HealthReport`)**:
   - 컴포넌트별 상태, 지연시간, 상세 메트릭(`details`)을 포함한 단일 JSON 출력(`toJson()`).

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.health.*;
import java.time.Duration;

public class HealthCheckExample {
    public static void main(String[] args) {
        // 1. 레지스트리 생성 및 헬스 체크 등록
        HealthRegistry registry = new HealthRegistry();

        // 디스크 검사 (임계치: 500MB 미만 시 DOWN, 2000MB 미만 시 DEGRADED)
        registry.register("disk", DiskSpaceCheck.ofCurrentDirectory(500, 2000));

        // JVM 메모리 검사 (사용률 85% 이상 시 DEGRADED, 95% 이상 시 DOWN)
        registry.register("memory", FreeMemoryCheck.ofRatios(0.85, 0.95));

        // 외부 서비스 TCP 핑 (1초 타임아웃)
        registry.register("db-ping", PingCheck.tcp("localhost", 5432, Duration.ofSeconds(1)));

        // 커스텀 체크 등록 (개별 타임아웃 500ms)
        registry.register("cache-service", () -> HealthResult.up("Redis alive"), Duration.ofMillis(500));

        // 2. 헬스 체커 실행 및 리포트 조회
        try (HealthChecker checker = new HealthChecker(registry)) {
            HealthReport report = checker.checkAll();

            System.out.println("Overall Status: " + report.overallStatus());
            System.out.println("Total Duration: " + report.totalDuration().toMillis() + "ms");

            // 3. JSON 포맷 출력
            String jsonReport = report.toJson(true);
            System.out.println(jsonReport);
        }
    }
}
```
