# 📦 `skill-concurrent-rate-limiter-concurrency-gate`

> **비동기 워크플로우(CompletableFuture & Virtual Threads)를 위한 최대 동시 실행 수 제어기**

- **아티팩트 좌표**: `com.msp:skill-concurrent-rate-limiter-concurrency-gate:1.0.0`
- **개별 JAR 산출물**: `modules/skill-concurrent-rate-limiter-concurrency-gate/build/libs/skill-concurrent-rate-limiter-concurrency-gate-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **최대 동시 실행 수 제한 (Max Concurrency)**:
   - 시스템 리소스(외부 API 요청, DB 커넥션 등)를 보호하기 위해 활성 실행 수를 정확히 제한.
2. **유계 대기 큐 및 신속 거절 (Bounded Queue & Fast Failure)**:
   - 큐 용량 초과 시 즉각 `GateCapacityExceededException`을 발생시켜 스레드/메모리 고갈 방지.
3. **대기 시간 초과 제어 (Queue Timeout)**:
   - 큐에서 대기하는 시간이 지정된 한도를 넘길 경우 `GateTimeoutException`으로 즉시 완료.
4. **비동기/논블로킹 래퍼 (`CompletableFuture`)**:
   - `supplyAsync(Supplier<CompletableFuture<T>>)`: 비동기 작업 완료 시 슬롯 자동 반환(`release()`).
   - `submit(Callable<T>)` / `runAsync(Runnable)`: 일반 동기 작업의 비동기 실행 제어.
5. **실시간 관측성 지표 (GateMetrics)**:
   - 활성 태스크 수, 대기 큐 깊이, 총 획득 수, 총 거절 수 실시간 조회.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.concurrencygate.ConcurrencyGate;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class ConcurrencyGateExample {
    public static void main(String[] args) {
        // 최대 동시 실행 5개, 대기 큐 최대 50개, 큐 대기 제한 5초
        try (ConcurrencyGate gate = ConcurrencyGate.of(5, 50, Duration.ofSeconds(5))) {

            // 비동기 작업 래핑
            CompletableFuture<String> future = gate.supplyAsync(() -> {
                System.out.println("Executing API call under concurrency limit...");
                return CompletableFuture.completedFuture("Result");
            });

            future.thenAccept(System.out::println).join();

            // 관측 지표 확인
            System.out.println("Active: " + gate.getActiveCount());
            System.out.println("Queue Depth: " + gate.getQueueDepth());
        }
    }
}
```
