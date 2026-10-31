# 📦 `skill-concurrent-rate-limiter-gcra`

> **순수 Java 21 표준 라이브러리 기반 초정밀 무잠금(Lock-Free AtomicLong CAS) GCRA (Generic Cell Rate Algorithm) 처리율 제한기**

- **아티팩트 좌표**: `com.msp:skill-concurrent-rate-limiter-gcra:1.0.0`
- **개별 JAR 산출물**: `modules/skill-concurrent-rate-limiter-gcra/build/libs/skill-concurrent-rate-limiter-gcra-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **GCRA (가상 스케줄링) 알고리즘**:
   - ATM 네트워크 및 Redis Cell, Nginx에서 널리 활용되는 리키 버킷(Leaky Bucket) 변형 알고리즘
   - 수많은 타임스탬프나 큐를 유지하지 않고 단 하나의 **Theoretical Arrival Time (TAT)** 타임스탬프만 추적
2. **초정밀 무잠금 (Lock-Free CAS) 고성능 아키텍처**:
   - `AtomicLong` CAS 루프로 구현되어 스레드 블로킹이나 Lock 경합 없이 초당 수백만 건 처리 가능
   - 가상 스레드(Virtual Threads) 환경에서 블로킹 없는 완벽한 동시성 제공
3. **유연한 설정 및 대기 시간 산출**:
   - 배출 주기(`emissionInterval`) 및 버스트 허용량(`burstTolerance`) 직접 지정
   - `fromRate(requestsPerSecond, maxBurst)` 편의 팩토리 제공
   - 거절 시 다음 토큰 인입까지 잔여 대기 시간(`getWaitTime()`) 실시간 계산
   - 대량 인입(`tryAcquire(int count)`) 지원

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.gcra.GcraRateLimiter;
import java.time.Duration;

public class GcraExample {
    public static void main(String[] args) {
        // 초당 10회 요청 허용, 최대 버스트 용량 5개
        GcraRateLimiter limiter = GcraRateLimiter.fromRate(10.0, 5);

        // 1. 단일 요청 허용 검사
        if (limiter.tryAcquire()) {
            System.out.println("요청 처리 성공");
        } else {
            Duration waitTime = limiter.getWaitTime();
            System.out.println("요청 초과 (Rate Limited). 대기 필요 시간: " + waitTime.toMillis() + "ms");
        }

        // 2. 일괄 요청 허용 검사 (Batch)
        boolean allowed = limiter.tryAcquire(3);
        System.out.println("3개 일괄 요청 허용 여부: " + allowed);

        // 3. 상태 리셋
        limiter.reset();
    }
}
```
