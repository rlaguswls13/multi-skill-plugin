# JWT Blacklist In-Memory Cache (`skill-jwt-blacklist-cache`)

로그아웃 또는 강제 만료된 JWT(또는 JTI)를 토큰 만료 시점(Exp)까지만 안전하게 보관하고 검증하는 경량 인메모리 블랙리스트 캐시 모듈입니다.

## Features
- **TTL & Expiration Binding**: 토큰 본래의 만료 시각(`exp`)을 키로 저장하여 무한정 메모리가 증가하는 문제를 원천 차단.
- **Lazy & Batch Eviction**: `isRevoked()` 호출 시 만료된 토큰 자동 축출 및 `cleanupExpired()` 일괄 정리 지원.
- **Thread-Safe**: `ConcurrentHashMap` 기반 락-프리 멀티스레드 동시성 지원.
- **Zero Dependencies**: 순수 Java 21 표준 라이브러리(`java.time`, `java.util.concurrent`).

## Usage

```java
JwtBlacklistCache cache = new JwtBlacklistCache();

// 1. 사용자 로그아웃 시 토큰 만료시각까지 블랙리스트 등록
Instant tokenExp = Instant.ofEpochSecond(decodedJwt.getClaim("exp").asLong());
cache.revoke(decodedJwt.getId(), tokenExp);

// 2. API 인증 필터에서 폐기 여부 검증
if (cache.isRevoked(jti)) {
    response.setStatus(401); // Unauthorized (Revoked Token)
    return;
}
```
