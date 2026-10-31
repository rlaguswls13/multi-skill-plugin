# 📦 `skill-ip-rate-limiter-token-sliding`

> **순수 Java 21 표준 라이브러리 기반 IP별 슬라이딩 토큰 버킷(Token Bucket) 처리율 제한기 및 유휴 IP 자동 축출 엔진**

- **아티팩트 좌표**: `com.msp:skill-ip-rate-limiter-token-sliding:1.0.0`
- **개별 JAR 산출물**: `modules/skill-ip-rate-limiter-token-sliding/build/libs/skill-ip-rate-limiter-token-sliding-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **Per-IP 슬라이딩 토큰 버킷 (Token Bucket Rate Limiting)**:
   - 클라이언트 IP 주소별 독립적인 토큰 버킷 생성 및 처리량 제한
   - 초당 토큰 충전율(`refillTokensPerSecond`) 및 최대 버스트 허용량(`capacity`) 정밀 제어
2. **유휴 IP 메모리 누수 방지 자동 정리 (`cleanupIdleIps`)**:
   - 지정된 유휴 시간(`idleTtl`) 동안 요청이 없는 비활성 IP 엔트리를 원자적으로 탐지 및 메모리 해제
   - 고밀도 분산 트래픽 환경에서도 안정적인 O(N) 인메모리 관리
3. **고성능 및 완벽한 스레드 안전성 (Thread-Safe)**:
   - `ConcurrentHashMap`과 세분화된 락(Lock)을 활용하여 초당 수십만 동시 요청 환경에서도 무경합 고속 처리
4. **제로 런타임 의존성**:
   - Guava, Redisson, Resilience4j 없이 순수 Java 21 표준 라이브러리만으로 동작

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.ipratelimit.IpRateLimiter;
import java.time.Duration;

public class RateLimiterExample {
    public static void main(String[] args) {
        // 1. 버킷 최대 용량 10개, 초당 2개 충전, 유휴 TTL 10분 설정
        IpRateLimiter limiter = new IpRateLimiter(10, 2.0, Duration.ofMinutes(10));

        String clientIp = "192.168.1.100";

        // 2. 토큰 획득 시도 (단일 토큰)
        if (limiter.tryAcquire(clientIp)) {
            System.out.println("요청 승인: " + clientIp);
        } else {
            System.out.println("429 Too Many Requests: 처리율 한도 초과");
        }

        // 3. 다중 토큰 일괄 소비
        boolean allowed = limiter.tryAcquire(clientIp, 5);
        System.out.println("5토큰 일괄 획득 성공 여부: " + allowed);
        System.out.println("잔여 토큰 수: " + limiter.getAvailableTokens(clientIp));

        // 4. 주기적 유휴 IP 정리 (스케줄러 또는 주기적 백그라운드 태스크)
        int evicted = limiter.cleanupIdleIps();
        System.out.println("정리된 비활성 IP 개수: " + evicted);
    }
}
```
