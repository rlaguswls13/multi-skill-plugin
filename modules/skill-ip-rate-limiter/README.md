# skill-ip-rate-limiter

클라이언트 IP별로 윈도우 처리율을 격리 추적하며, DDoS 및 무차별 대입 공격을 방어하고, 비활성 IP 버킷을 주기적으로 자동 정리(`cleanupExpired`)하여 메모리 누수를 방지하는 멀티테넌트 IP 처리율 제한 모듈입니다.

## 주요 기능
- `IpRateLimiter.of(maxPermits, window)`: IP별 윈도우 한도 설정
- `limiter.tryAcquire(clientIp)`: 클라이언트 IP별 처리율 평가 및 잔여/재시도 대기시간 반환
- `limiter.cleanupExpired()`: 유휴 IP 버킷 제거 (메모리 누수 방지)
