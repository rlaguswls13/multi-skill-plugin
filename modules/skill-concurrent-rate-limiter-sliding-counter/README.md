# skill-concurrent-rate-limiter-sliding-counter

고처리량(High-Throughput), 초저메모리(Low-Memory) 슬라이딩 윈도우 카운터(Sliding Window Counter) 처리율 제한기(Rate Limiter) 라이브러리입니다.
전체 타임 윈도우를 $M$개의 서브 슬롯(Sub-slots)으로 쪼갠 원형 링(Circular Atomic Ring) 구조로 동작하여, 슬라이딩 로그(Sliding Log) 대비 메모리 사용량을 획기적으로 절감하고 $O(1)$의 시간 복잡도를 보장합니다.

## 주요 기능
- **서브 슬롯 분할 슬라이딩 윈도우 카운터**: 윈도우를 $M$개의 슬롯으로 세분화하여 경계 지점의 급격한 버스트 트래픽 오차를 해소
- **Lock-Free 고성능 동시성 제어**: 락 획득 없이 `AtomicReferenceArray` 및 CAS(Compare-And-Set) 루프 기반으로 스레드 안전성 보장
- **극저 메모리 점유 ($O(M)$)**: 모든 요청의 타임스탬프를 보관하는 슬라이딩 로그와 달리, 슬롯 개수($M$, 예: 10~60개)만큼의 고정 메모리만 소비
- **결정론적 시간 공급자(TimeProvider) 지원**: 모의 시계(`LongSupplier`)를 주입하여 단위 테스트 및 시뮬레이션을 신속하게 검증
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리 기반

## 사용 예제

```java
import com.msp.slidingcounter.SlidingCounterRateLimiter;
import java.time.Duration;

public class Example {
    public static void main(String[] args) {
        // 1초(1000ms) 윈도우를 10개의 슬롯(슬롯당 100ms)으로 분할, 최대 100건 허용
        SlidingCounterRateLimiter limiter = new SlidingCounterRateLimiter(
            100,
            Duration.ofSeconds(1),
            10
        );

        // 단일 토큰 획득
        if (limiter.tryAcquire()) {
            System.out.println("Request allowed");
        } else {
            System.out.println("Rate limit exceeded (HTTP 429)");
        }

        // 다중 토큰 획득 (예: 배치 작업용 5건)
        if (limiter.tryAcquire(5)) {
            System.out.println("Batch allowed");
        }

        // 현재 슬라이딩 윈도우 내 누적 요청 수
        long activeCount = limiter.getCurrentCount();
        System.out.println("Active requests in window: " + activeCount);
    }
}
```
