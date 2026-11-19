# 📦 `skill-concurrent-rate-limiter-leaky-token-dual`

> **Token Bucket의 버스트 허용성과 Leaky Bucket의 균등 방출을 결합한 2단계 하이브리드 속도 제한기 (Dual-Stage Rate Limiter)**

- **아티팩트 좌표**: `com.msp:skill-concurrent-rate-limiter-leaky-token-dual:1.0.0`
- **개별 JAR 산출물**: `modules/skill-concurrent-rate-limiter-leaky-token-dual/build/libs/skill-concurrent-rate-limiter-leaky-token-dual-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **2단계 하이브리드 트래픽 제어 파이프라인**:
   - **Stage 1 (Token Bucket)**: 순간적인 유입 스파이크(Burst)를 `tokenCapacity` 한도 내에서 유연하게 수용
   - **Stage 2 (Leaky Bucket)**: 수용된 요청을 큐(`queueCapacity`)에 완충한 뒤 하류 시스템(Downstream API/DB)으로 초당 `leakRate`의 균등한 간격으로 매끄럽게(Smooth) 방출
2. **동기 및 비동기 스케줄링 API**:
   - `boolean tryAcquire()`: 대기 없는 즉시 유입 검증
   - `CompletableFuture<T> executeScheduled(Supplier<T> task)`: 비동기 큐잉 및 균등 간격 순차 실행 보장
3. **토큰 환불(Refund) 및 엄격한 격리**:
   - 2단계 리키 버킷 큐가 가득 찼을 경우 소비했던 1단계 토큰을 즉시 반환하여 정밀한 자원 정합성 유지
4. **실시간 관측성 (Observability)**:
   - 가용 토큰 수, 큐 대기 건수, 누적 수용/거절 횟수 및 예상 방출 지연 시간(`estimatedQueueDelay()`) 스냅샷 제공

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.duallimiter.DualRateLimiter;
import java.util.concurrent.CompletableFuture;

public class DualLimiterExample {
    public static void main(String[] args) throws Exception {
        // 버스트 50회 허용, 초당 10회 토큰 리필, 초당 5회 균등 방출, 큐 용량 100
        try (DualRateLimiter limiter = DualRateLimiter.builder()
                .tokenCapacity(50.0)
                .tokenRefillRate(10.0)
                .leakRate(5.0)
                .queueCapacity(100)
                .build()) {

            // 1. 동기 검사
            if (limiter.tryAcquire()) {
                System.out.println("동기 요청 즉시 승인");
            }

            // 2. 비동기 균등 방출 스케줄링
            CompletableFuture<String> future = limiter.executeScheduled(() -> {
                return "하류 서비스 API 호출 완료";
            });

            future.thenAccept(System.out::println);

            // 3. 상태 스냅샷 확인
            System.out.println("가용 토큰: " + limiter.availableTokens());
            System.out.println("대기 큐: " + limiter.leakQueueSize());
            System.out.println("예상 지연: " + limiter.estimatedDelay());
        }
    }
}
```
