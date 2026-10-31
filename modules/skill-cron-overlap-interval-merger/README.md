# skill-cron-overlap-interval-merger

다양한 주기(5-field Cron)와 예상 소요 시간(Duration)을 가진 여러 배치/스케줄러 작업들이 특정 시간 윈도우 내에서 중복 실행되거나 인접하여 실행될 때, 중첩(Overlapping) 및 연속(Contiguous) 실행 구간을 단일 비중복 시간 윈도우(`TimeInterval`)로 정규화 및 병합하는 순수 Java 21 라이브러리입니다.

배치 부하 예측, DB 점검 윈도우 산출, 클러스터 스케줄 잠금 구간 병합 등에 유용합니다.

---

## 🚀 주요 기능

- **`TimeInterval` 레코드**: `start`, `end`, `duration()`, `overlaps()`, `isContiguousWith()`, `merge()` 지원
- **구간 병합 (`mergeIntervals`)**: 임의의 시간 간격 리스트를 정렬한 후 중첩되거나 맞닿은(Contiguous) 구간들을 단일 연속 구간으로 결합
- **크론 실행 윈도우 전개 (`calculateMergedExecutionWindows`)**: 5-field 표준 Cron(`분 시 일 월 요일`)을 해석하여 윈도우 내 모든 작업 실행 간격을 생성하고 병합
- **자체 중첩 및 다중 작업 지원**: 주기가 실행 시간보다 짧아 자기 자신과 중첩되는 작업(Overrun) 및 복수 작업 간의 충돌 구간을 포괄 통합
- **Zero-Dependency**: 외부 스케줄러(Quartz, Spring 등) 의존성 없는 100% JDK 21 표준 구현

---

## 📦 사용 예시

```java
import com.msp.cronmerge.CronIntervalMerger;
import com.msp.cronmerge.ScheduledJob;
import com.msp.cronmerge.TimeInterval;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class CronMergeExample {
    public static void main(String[] args) {
        // 1. 작업 정의
        ScheduledJob syncJob = new ScheduledJob("DataSync", "0 10 * * *", Duration.ofMinutes(25));
        ScheduledJob reportJob = new ScheduledJob("DailyReport", "20 10 * * *", Duration.ofMinutes(20));

        Instant windowStart = Instant.parse("2026-10-31T09:00:00Z");
        Instant windowEnd = Instant.parse("2026-10-31T12:00:00Z");

        // 2. 실행 윈도우 산출 및 중첩 병합
        List<TimeInterval> windows = CronIntervalMerger.calculateMergedExecutionWindows(
                List.of(syncJob, reportJob), windowStart, windowEnd
        );

        // syncJob (10:00~10:25)과 reportJob (10:20~10:40)이 병합되어 [10:00, 10:40] 단일 윈도우 생성
        for (TimeInterval interval : windows) {
            System.out.println("Execution Window: " + interval.start() + " ~ " + interval.end()
                    + " (Duration: " + interval.duration().toMinutes() + " mins)");
        }
    }
}
```

---

## 🧪 테스트 검증 매트릭스

- `testTimeIntervalBasics`: 불변 레코드 속성 및 시작/종료 시점 유효성 검증
- `testTimeIntervalOperations`: 중첩(`overlaps`), 인접(`isContiguousWith`), 병합(`merge`), 분리 구간 예외 검증
- `testMergeIntervals`: 중첩/인접/포함/분리 구간 다중 병합 알고리즘 검증
- `testMergeIntervalsBoundary`: 빈 리스트, 단일 구간 등 경계 조건 처리 검증
- `testCalculateMergedWindowsSelfOverlapping`: 주기가 소요 시간보다 짧은 자체 중첩(Self-Overlapping) 작업의 통합 윈도우 계산 검증
- `testCalculateMergedWindowsMultipleJobs`: 복수 작업 간 중첩 및 독립 윈도우 동시 산출 검증
