# Resilience Bulkhead Gate (`skill-resilience-bulkhead-gate`)

마이크로서비스 및 고부하 분산 시스템에서 특정 컴포넌트나 외부 API의 병목/지연으로 인해 애플리케이션 전체 스레드 풀이 고갈되는 연쇄 장애(Cascading Failure)를 방지하는 벌크헤드(Bulkhead, 격벽) 동시성 격리 모듈입니다.

외부 무거운 라이브러리(Resilience4j 등) 없이 Java 21 표준 라이브러리(`Semaphore`, `AtomicInteger`)만을 사용하여 가볍고 안전하게 동시성을 통제합니다.

## 주요 기능

- **동시 실행 한도 격리 (Max Concurrent Calls)**:
  - 지정된 허용량 이상의 동시 실행을 원천 차단하여 시스템 리소스 고갈 방지
- **유계 대기 큐 및 타임아웃 (Bounded Wait Queue & Timeout)**:
  - 허가권이 소진되었을 때 무한 대기하지 않고, 지정된 큐 깊이(`maxWaitQueueSize`) 및 대기 시간(`maxWaitDuration`) 내에서만 대기
  - 큐 용량 초과 또는 대기 시간 만료 시 즉시 `BulkheadFullException` 빠른 실패(Fail-Fast)
- **Supplier / Runnable / Callable 함수형 실행 지원**:
  - `executeSupplier`, `executeRunnable`, `executeCallable` 메서드로 try-finally 자원 반환 자동화
- **실시간 메트릭 모니터링 (`BulkheadMetrics`)**:
  - 가용 허가권 수, 현재 실행 중인 호출 수, 대기 중인 큐 깊이 조회 기능 제공

## 사용 예제

```java
import com.msp.bulkhead.BulkheadConfig;
import com.msp.bulkhead.BulkheadFullException;
import com.msp.bulkhead.BulkheadGate;
import java.time.Duration;

// 1. 벌크헤드 게이트 설정 (최대 동시 호출 5개, 대기 큐 10개, 최대 대기시간 1초)
BulkheadConfig config = BulkheadConfig.of(5, 10, Duration.ofSeconds(1));
BulkheadGate gate = new BulkheadGate(config);

// 2. 동시성 격리 하에서 비즈니스 로직 실행
try {
    String response = gate.executeSupplier(() -> {
        // 외부 결제 API 호출 또는 고부하 DB 쿼리
        return callExternalPaymentGateway();
    });
    System.out.println("Result: " + response);
} catch (BulkheadFullException e) {
    // 동시 호출 초과 및 대기 큐 만료 시 Fail-Fast 처리
    System.err.println("Bulkhead is full, fallback triggered: " + e.getMessage());
}

// 3. 실시간 메트릭 조회
System.out.println("Available permits: " + gate.getAvailablePermits());
System.out.println("Queue depth: " + gate.getQueueDepth());
System.out.println("Metrics: " + gate.getMetrics());
```
