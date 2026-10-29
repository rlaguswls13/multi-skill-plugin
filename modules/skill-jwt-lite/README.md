# 🎫 skill-jwt-lite

순수 Java 21 표준 라이브러리 기반 경량 JWT (JSON Web Token) 라이브러리 (JJWT, Nimbus, Jackson 의존성 Zero).

RFC 7519 규격을 완벽 준수하며, 대칭키 HS256(HMAC-SHA256) 및 비대칭키 RS256(RSA-SHA256) 토큰 발급/서명 검증, 만료 시간(exp), 발효 시각(nbf), 발급자(iss), 대상(aud) 검증을 지원합니다.

---

## 🚀 주요 기능

1. **외부 런타임 의존성 Zero**:
   - Jackson, Gson, JJWT, Nimbus 없이 Java 21 표준 암호화 라이브러리와 내장 경량 JSON 파서로 완전 자립 동작.
2. **지원 알고리즘**:
   - **HS256**: HMAC-SHA256 (상수 시간 타이밍 세이프 검증).
   - **RS256**: RSA-SHA256 (`PrivateKey` 서명 / `PublicKey` 검증).
3. **JwtBuilder (유연한 빌더)**:
   - Fluent API로 표준 클레임(`sub`, `iss`, `aud`, `iat`, `exp`, `nbf`, `jti`) 및 커스텀 클레임 조합.
4. **JwtParser (엄격한 검증기)**:
   - 서명 위변조 방지 (`JwtSignatureException`).
   - 만료 시간 자동 검사 (`JwtExpiredException`) 및 시간 오차 허용(`clockSkew`).
   - 사전 발효 방지 (`JwtPrematureException`).
   - 발급자/대상/주체 불일치 검증 (`JwtValidationException`).

---

## 💻 사용 예제

### 1. HS256 토큰 발급 및 검증

```java
import com.msp.jwt.JwtLite;
import com.msp.jwt.JwtToken;
import java.time.Instant;

String secretKey = "my-secret-key-at-least-256-bits-long!!";

// 1. 토큰 발급 (컴팩트 Base64Url 문자열)
String token = JwtLite.builder()
        .subject("user@example.com")
        .issuer("https://my-auth-server.com")
        .audience("api-service")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600)) // 1시간 만료
        .claim("userId", 1001L)
        .claim("role", "ADMIN")
        .signWith(JwtLite.hs256(secretKey))
        .compact();

// 2. 토큰 검증 및 클레임 파싱
JwtToken jwt = JwtLite.parser()
        .verifyWith(JwtLite.hs256(secretKey))
        .requireIssuer("https://my-auth-server.com")
        .requireAudience("api-service")
        .parse(token);

System.out.println("Subject: " + jwt.subject());
System.out.println("Role: " + jwt.claims().getString("role"));
System.out.println("Expires: " + jwt.expiresAt());
```

### 2. RS256 비대칭키 토큰 발급 및 검증

```java
import com.msp.jwt.JwtLite;
import com.msp.jwt.JwtToken;
import java.security.PrivateKey;
import java.security.PublicKey;

PrivateKey privateKey = ...; // 인증 서버의 개인키
PublicKey publicKey = ...;   // 클라이언트/게이트웨이의 공개키

// 인증 서버: PrivateKey로 서명
String token = JwtLite.builder()
        .subject("enterprise-user")
        .issuer("https://auth.enterprise.com")
        .signWith(JwtLite.rs256(privateKey))
        .compact();

// 리소스 서버: PublicKey로 서명 검증
JwtToken verified = JwtLite.parser()
        .verifyWith(JwtLite.rs256(publicKey))
        .parse(token);
```

---

## 🧪 빌드 및 테스트

```bash
.\gradlew.bat :modules:skill-jwt-lite:test
```
