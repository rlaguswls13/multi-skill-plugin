# ⏱️ `skill-metrics-timer`

> **외부 프레임워크 의존성 없이 메서드 실행 지연 시간 분포(Min, Max, Avg, P50, P90, P95, P99) 및 실시간 처리량(TPS)을 측정하고 리포트를 생성하는 인메모리 메트릭 타이머 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-metrics-timer:1.0.0`
- **개별 JAR 산출물**: `modules/skill-metrics-timer/build/libs/skill-metrics-timer-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **간편한 실행 시간 측정 (`MetricsTimer`, `Metrics`)**:
   - `time(metricName, Runnable)` 및 `time(metricName, Supplier<T>)` 지원.
   - `try-with-resources` 구문을 지원하는 `TimerSample` (`Metrics.start(metricName)`).
2. **정밀 히스토그램 분위수 (Percentiles)**:
   - Reservoir 기반 샘플링 및 선형 보간을 통한 P50, P90, P95, P99 백분위수 산출.
3. **슬라이딩 윈도우 처리량 (TPS)**:
   - 락프리 시간 버킷 기반 실시간 초당 트랜잭션 수(Transactions Per Second) 계산.
4. **포맷 리포트 생성 (`MetricsReport`)**:
   - 콘솔/로그용 ASCII 테이블 출력 (`toTable()`) 및 JSON 직렬화 (`toJson()`).
5. **동시성 안전 (Thread-Safe)**:
   - `LongAdder`, `AtomicLong`, 가상 스레드 환경에 최적화된 동시성 처리.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.metrics.*;
import java.time.Duration;

// 1. Supplier / Runnable 람다 측정
String result = Metrics.time("db.getUserById", () -> {
    return fetchUserFromDatabase("user-101");
});

// 2. try-with-resources 구문 기반 블록 측정
try (TimerSample sample = Metrics.start("order.checkout")) {
    processPayment();
    validateInventory();
}

// 3. 직접 소요 시간 기록
Metrics.record("cache.lookup", Duration.ofMillis(2));

// 4. 특정 지표 스냅샷 조회
Metrics.snapshot("order.checkout").ifPresent(snap -> {
    System.out.printf("Count: %d, Avg: %.2f ms, P99: %.2f ms, TPS: %.1f%n",
            snap.count(), snap.meanMillis(), snap.p99Millis(), snap.tps());
});

// 5. 전체 메트릭 요약 리포트 (ASCII 테이블 또는 JSON)
MetricsReport report = Metrics.report();
System.out.println(report.toTable());
System.out.println(report.toJson());
```
