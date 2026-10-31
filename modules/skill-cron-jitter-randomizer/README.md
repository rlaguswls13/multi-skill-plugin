# skill-cron-jitter-randomizer

대규모 분산 배치 및 크론 작업이 정각에 일제히 집중되어 DB 및 외부 API에 폭주 부하를 유발하는 Thundering Herd (스탬피드) 현상을 방어하기 위한 다중 전략 지터(Jitter) 및 재시도 지수 백오프 무작위화 엔진입니다.

## 주요 기능
- **다중 지터 전략 지원 (`JitterStrategy`)**:
  - `FULL`: `[0, Max]` 균등 무작위 지터
  - `EQUAL`: `[Max/2, Max]` 절반 고정 + 절반 무작위 지터
  - `DECORRELATED`: 직전 백오프 값에 비례하여 증폭되는 분산 환경 권장 비상관 지터 (AWS Marc Brooker 패턴)
- **지연 시간 산출 (`computeBackoffJitter`)**: 지수 백오프 및 상한 캡(Cap)을 고려한 안전한 재시도 지터 계산
- **결정론적 시드 지원**: `new CronJitterRandomizer(seed)` 또는 `new CronJitterRandomizer(random)`를 통해 단위 테스트에서 100% 재현 가능한 지터 보장
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리 및 `ThreadLocalRandom` 기반

## 사용 예제

```java
import com.msp.cronjitter.CronJitterRandomizer;
import com.msp.cronjitter.JitterStrategy;
import java.time.Duration;
import java.time.Instant;

public class Example {
    public static void main(String[] args) {
        Instant cronScheduled = Instant.parse("2026-11-01T00:00:00Z");

        // 1. Equal Jitter 적용 (예약 시각 기준 [15초, 30초] 지연 분산)
        Instant executionTime = CronJitterRandomizer.applyJitter(
            cronScheduled,
            Duration.ofSeconds(30),
            JitterStrategy.EQUAL
        );
        System.out.println("Execution time with jitter: " + executionTime);

        // 2. 재시도 지수 백오프 계산 (재시도 3회차, 100ms 기본, 최대 10초, DECORRELATED 전략)
        Duration backoff = CronJitterRandomizer.computeBackoffJitter(
            Duration.ofMillis(100),
            Duration.ofSeconds(10),
            3,
            JitterStrategy.DECORRELATED
        );
        System.out.println("Retry backoff duration: " + backoff.toMillis() + "ms");
    }
}
```
