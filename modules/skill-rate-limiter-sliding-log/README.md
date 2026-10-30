# Sliding Log Rate Limiter (`skill-rate-limiter-sliding-log`)

마이크로초(µs) 정밀도의 슬라이딩 로그(Sliding Log) 알고리즘을 구현한 고성능 레이트 리미터(Rate Limiter)입니다.

기존 고정 윈도우(Fixed Window)나 슬라이딩 윈도우 카운터(Sliding Window Counter)의 경계 시간 집중(Burst Spike) 문제를 원천 차단하여, 정확한 요청 타임스탬프 슬라이딩을 통해 정밀한 API 트래픽 제어를 보장합니다.

## Features

- **슬라이딩 로그 (Sliding Log) 알고리즘**: 요청 발생 시각을 큐에 기록하고 윈도우 밖으로 벗어난 로그만 정밀하게 롤링 배출
- **키별 파티셔닝 지원**: IP 주소, 테넌트 ID, API 토큰, 사용자 ID 등 임의의 키 단위 격리 제어
- **스레드 세이프 & 락 분할**: `ConcurrentHashMap`과 키별 세분화 동기화(Per-key lock)를 통한 멀티스레드 동시성 안전 보장
- **유휴(Stale) 파티션 자동 정리**: 메모리 누수를 방지하기 위해 일정 기간 비활성 상태의 키 큐 자동 수거(Eviction)
- **Zero Runtime Dependencies**: Guava, Redis, Resilience4j 등의 외부 프레임워크 없이 순수 Java 21 표준 라이브러리만 사용

## Usage

```java
import com.msp.slidinglog.SlidingLogRateLimiter;
import java.time.Duration;

SlidingLogRateLimiter rateLimiter = new SlidingLogRateLimiter();

// 1. 단일(글로벌) 레이트 리미팅: 1초당 최대 5건 허용
Duration window = Duration.ofSeconds(1);
int maxRequests = 5;

if (rateLimiter.tryAcquire(maxRequests, window)) {
    // 요청 처리 허용
    processRequest();
} else {
    // 429 Too Many Requests 응답
    System.out.println("요청 한도 초과 (Rate limit exceeded)");
}

// 2. 클라이언트 IP/API 키별 개별 분할 리미팅
String clientIp = "192.168.1.100";
if (rateLimiter.tryAcquire(clientIp, 10, Duration.ofMinutes(1))) {
    System.out.println("IP별 요청 승인. 잔여 쿼터: " + 
        rateLimiter.getRemainingPermits(clientIp, 10, Duration.ofMinutes(1)));
}

// 3. 비활성 유휴 키 자동 또는 수동 수거 (10분 이상 무활동 키 해제)
int evicted = rateLimiter.evictStale(Duration.ofMinutes(10));
System.out.println("정리된 유휴 세션 수: " + evicted);
```
