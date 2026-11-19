# 📦 `skill-cron-execution-history-tracker`

> **순수 Java 21 표준 라이브러리 기반 Cron 실행 이력 추적기, 스케줄 지연(Drift) 감지 및 작업 이상 징후 분석기**

- **아티팩트 좌표**: `com.msp:skill-cron-execution-history-tracker:1.0.0`
- **개별 JAR 산출물**: `modules/skill-cron-execution-history-tracker/build/libs/skill-cron-execution-history-tracker-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **정밀 실행 이력 기록 (`ExecutionRecord`)**:
   - `recordExecution(String taskId, Instant scheduledTime, Instant actualStartTime, Instant finishTime, boolean success)`
   - 스케줄 지연(Drift): `actualStartTime - scheduledTime` (스레드 고갈 및 대기열 지연 감지).
   - 작업 소요 시간(Duration): `finishTime - actualStartTime`.
2. **롤링 실행 성능 통계 (`ExecutionStatistics`)**:
   - 표본 수, 성공률, 실패 건수.
   - 스케줄 드리프트: 평균, 최소, 최대, P50, P90, P95, P99 (밀리초).
   - 실행 지속 시간: 평균, 최소, 최대, P50, P95, P99 및 표준 편차(StdDev).
3. **이상 징후 자동 탐지 및 알림 (`AnomalyEvent`)**:
   - `SEVERE_DRIFT`: 스케줄 지연이 임계치 초과 시 경고 (스레드 풀 포화 및 OS 부하).
   - `EXECUTION_OVERRUN`: 작업 소요 시간이 주기(Interval)를 초과하여 차기 작업과의 중복 실행 위험 발생 시 감지.
   - `FAILURE_SPIKE`: 연속 작업 실패 임계치 도달 시 감지.
   - `addAnomalyListener(Consumer<AnomalyEvent>)`를 통한 비동기 알림 및 모니터링 연동.
4. **고정 윈도우 메모리 보호**:
   - 테스크별 최대 보유 기록 수(기본 200건) 초과 시 가장 오래된 기록 자동 방출.
   - 스레드 안전성 보장 (`ConcurrentHashMap`).

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.cronhist.*;
import java.time.Duration;
import java.time.Instant;

public class Example {
    public static void main(String[] args) {
        // 1. 트래커 초기화 및 이상 징후 정책 설정
        CronExecutionTracker tracker = new CronExecutionTracker(100);

        tracker.setTaskPolicy("db-backup-job", AnomalyPolicy.builder()
                .maxAllowedDrift(Duration.ofSeconds(3))           // 지연 3초 초과 시 경고
                .expectedInterval(Duration.ofMinutes(10))        // 실행 주기 10분
                .maxAllowedDuration(Duration.ofMinutes(8))       // 최대 8분 내 완료 요망
                .consecutiveFailureThreshold(2)                  // 2회 연속 실패 시 경고
                .build());

        // 2. 이상 징후 리스너 등록
        tracker.addAnomalyListener(event -> {
            System.err.printf("[ANOMALY DETECTED] Task: %s, Type: %s, Details: %s%n",
                    event.taskId(), event.type(), event.message());
        });

        // 3. 실행 기록
        Instant scheduled = Instant.now();
        Instant actualStart = scheduled.plusMillis(150);
        Instant finish = actualStart.plusSeconds(12);

        tracker.recordExecution("db-backup-job", scheduled, actualStart, finish, true);

        // 4. 통계 조회
        tracker.getStatistics("db-backup-job").ifPresent(stats -> {
            System.out.printf("Task: %s | Success Rate: %.1f%% | Mean Drift: %d ms | Mean Duration: %d ms%n",
                    stats.taskId(), stats.successRate() * 100, stats.meanDrift().toMillis(), stats.meanDuration().toMillis());
        });
    }
}
```
