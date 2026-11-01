# 📦 `skill-jwt-opaque-token-exchanger`

> **RFC 8693 OAuth 2.0 Token Exchange 패턴 기반 인메모리 불투명 참조 토큰(Opaque Reference Token) 및 JWT Claims 교환 레지스트리**

- **아티팩트 좌표**: `com.msp:skill-jwt-opaque-token-exchanger:1.0.0`
- **개별 JAR 산출물**: `modules/skill-jwt-opaque-token-exchanger/build/libs/skill-jwt-opaque-token-exchanger-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **RFC 8693 토큰 교환 (Token Exchange) 패턴**:
   - 외부 클라이언트에는 256비트 암호화 난수 기반의 불투명 참조 토큰(Opaque Token, 43자리 URL-Safe Base64) 발급.
   - 내부 시스템에서는 토큰을 역참조하여 풍부한 JWT 스타일 Claims 맵으로 즉시 변환.
2. **동시성 안전 (Thread-Safe) 및 메모리 관리**:
   - `ConcurrentHashMap` 기반 락-프리 고성능 저장소.
   - 만료 시간(TTL) 자동 검증, 조회 시 지연 정리(Lazy Purge), 배치 만료 토큰 정리(`purgeExpired`).
   - 토큰 즉시 폐기/무효화(`revoke`).
3. **Java 21 불변 Record 및 Clock 추상화**:
   - 불변 `OpaqueTokenRecord` 레코드 제공.
   - 테스트 친화적인 `Clock` 의존성 주입 지원.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.tokenx.TokenExchangeRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class TokenExchangeExample {
    public static void main(String[] args) {
        TokenExchangeRegistry registry = new TokenExchangeRegistry();

        // 1. 내부 JWT 클레임을 불투명 토큰(Opaque Token)으로 발급 (TTL 1시간)
        Map<String, Object> claims = Map.of(
                "sub", "user_987654",
                "scope", "read write profile",
                "org_id", "enterprise_01"
        );
        String opaqueToken = registry.issueOpaqueToken(claims, Duration.ofHours(1));
        System.out.println("Issued Opaque Token: " + opaqueToken); // e.g. 43-character URL-safe string

        // 2. 외부 API 요청 수신 시 불투명 토큰 -> Claims 교환
        Optional<Map<String, Object>> resolved = registry.resolveClaims(opaqueToken);
        resolved.ifPresent(c -> {
            System.out.println("Authenticated Subject: " + c.get("sub"));
            System.out.println("User Scope: " + c.get("scope"));
        });

        // 3. 로그아웃 / 세션 만료 시 토큰 즉시 폐기
        boolean revoked = registry.revoke(opaqueToken);
        System.out.println("Token Revoked: " + revoked);
    }
}
```
