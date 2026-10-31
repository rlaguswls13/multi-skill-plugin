# Retry Backoff & Jitter Calculator (`skill-retry-backoff-jitter`)

분산 시스템 장애 및 속도 제한(Rate Limiting) 발생 시, 재시도 요청이 특정 시점에 일제히 몰려 대상 서비스를 재차 다운시키는 **Thundering Herd(스탬피드) 현상**을 원천 차단하기 위한 AWS 및 Google 아키텍처 표준 지수 백오프 및 지터(Jitter) 계산기 모듈입니다.

순수 Java 21 표준 라이브러리(`java.time.Duration`, `java.util.random.RandomGenerator`)만을 사용하여 고성능으로 동작합니다.

## 주요 기능

- **AWS / Google 표준 지터 전략 4종 지원 (`JitterStrategy`)**:
  - `NONE`: 지수 백오프만 적용 ($temp = \min(maxDelay, base \times multiplier^{attempt})$)
  - `FULL_JITTER`: $[0, temp]$ 범위에서 균등 무작위 지연 (AWS 권장 기본값)
  - `EQUAL_JITTER`: $temp / 2 + rand(0, temp / 2)$ (최소 대기 시간 보장형)
  - `DECORRELATED_JITTER`: 이전 지연 시간을 바탕으로 비상관 지터 산출 ($\min(maxDelay, rand(base, prevSleep \times 3))$)
- **오버플로우 방어**:
  - 재시도 횟수가 매우 큰 값(e.g. 50, 100회)으로 증가하더라도 부동소수점 무한대/오버플로우 없이 안전하게 `maxDelay` 상한선 유지
- **결정론적 / 스레드 로컬 난수 지원**:
  - 단위 테스트 및 시뮬레이션을 위한 커스텀 `RandomGenerator` 주입 가능

## 사용 예제

```java
import com.msp.backoff.BackoffCalculator;
import com.msp.backoff.JitterStrategy;
import java.time.Duration;

Duration initialDelay = Duration.ofMillis(100);
Duration maxDelay = Duration.ofSeconds(10);

// 1. 기본 Full Jitter 지연 계산 (multiplier 2.0)
for (int attempt = 0; attempt < 5; attempt++) {
    Duration sleep = BackoffCalculator.calculateDelay(attempt, initialDelay, maxDelay);
    System.out.println("Attempt " + attempt + " sleep: " + sleep.toMillis() + "ms");
}

// 2. Equal Jitter 전략 (최소 절반의 지연 보장)
Duration equalJitter = BackoffCalculator.calculateDelay(
    3, 
    initialDelay, 
    maxDelay, 
    2.0, 
    JitterStrategy.EQUAL_JITTER
);
System.out.println("Equal Jitter delay: " + equalJitter.toMillis() + "ms");

// 3. Decorrelated Jitter 연속 계산
Duration prevSleep = Duration.ofMillis(200);
Duration nextSleep = BackoffCalculator.calculateDecorrelatedDelay(prevSleep, initialDelay, maxDelay);
System.out.println("Next decorrelated delay: " + nextSleep.toMillis() + "ms");
```
