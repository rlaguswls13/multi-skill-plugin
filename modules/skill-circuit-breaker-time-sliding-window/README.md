# 📦 `skill-circuit-breaker-time-sliding-window`

> **순수 Java 21 표준 라이브러리 기반 원형 버킷(Circular Buckets) 슬라이딩 시간 윈도우 서킷 브레이커**

- **아티팩트 좌표**: `com.msp:skill-circuit-breaker-time-sliding-window:1.0.0`
- **개별 JAR 산출물**: `modules/skill-circuit-breaker-time-sliding-window/build/libs/skill-circuit-breaker-time-sliding-window-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **원형 버킷(Circular Ring Buckets) 슬라이딩 윈도우**:
   - 시간 윈도우를 $N$개의 원형 버킷으로 균등 분할하여 $O(1)$ 시간 및 극소 메모리 오버헤드로 실패율 집계
2. **엄격한 서킷 상태 머신 (CLOSED &harr; OPEN &harr; HALF_OPEN)**:
   - **CLOSED**: 정상 통과. 최소 호출 건수(`minCallCount`) 충족 및 실패율 임계치(`failureRateThreshold`) 초과 시 즉시 OPEN 전이
   - **OPEN**: 진입 차단. `openDuration` 경과 후 자동으로 HALF_OPEN으로 시범 전환
   - **HALF_OPEN**: 단일 시범 호출 허용. 성공 시 즉시 CLOSED 복구, 실패 시 즉시 OPEN 재차단
3. **스레드 세이프 (Thread-Safe) 동시성 지원**:
   - `ReentrantLock` 기반 원자적 상태 전이 및 레이스 컨디션 방지
4. **결정론적 테스트 가능 설계 (Deterministic Clock Injection)**:
   - 시스템 시계(`Clock.systemUTC()`) 외에도 사용자 정의 `Clock` 주입을 지원하여 `Thread.sleep` 없이 정밀 단위 테스트 가능

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.timewindowcircuit.TimeWindowCircuitBreaker;
import com.msp.timewindowcircuit.CircuitState;

import java.time.Duration;

public class CircuitBreakerExample {
    public static void main(String[] args) {
        // 최근 10초 윈도우 동안 실패율이 50% 이상이면 차단, 5초 후 시범 재시도
        TimeWindowCircuitBreaker breaker = new TimeWindowCircuitBreaker(
                Duration.ofSeconds(10), // 모니터링 윈도우
                50,                     // 실패율 임계치 (%)
                Duration.ofSeconds(5)   // 차단 지속 시간
        );

        if (breaker.tryAcquirePermission()) {
            try {
                // 외부 API 호출 또는 원격 DB 쿼리 실행
                callExternalService();
                breaker.recordSuccess();
            } catch (Exception ex) {
                breaker.recordFailure();
                System.err.println("Execution failed: " + ex.getMessage());
            }
        } else {
            System.out.println("Circuit is OPEN! Request rejected immediately (Fast-Fail)");
        }

        System.out.printf("Current State: %s, Failure Rate: %.2f%%%n",
                breaker.getState(), breaker.getFailureRate());
    }

    private static void callExternalService() {
        // Remote call logic
    }
}
```
