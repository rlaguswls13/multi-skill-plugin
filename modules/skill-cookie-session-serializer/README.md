# Signed Session Cookie Serializer (`skill-cookie-session-serializer`)

HMAC-SHA256 전자서명과 만료 시각(TTL)으로 변조를 방지하는 고성능 무상태(Stateless) 세션 쿠키 직렬화/역직렬화 유틸리티입니다.

Spring Session이나 외부 Redis/DB 세션 저장소 없이도 순수 Java 21 표준 라이브러리만으로 안전하게 클라이언트 측 암호학적 쿠키 세션을 운용할 수 있습니다.

## Features

- **HMAC-SHA256 전자서명**: 세션 속성 페이로드 및 만료 시각을 서명하여 클라이언트 임의 변조 방지
- **URL-Safe 쿠키 포맷**: `Base64Url(Payload).EpochSeconds.Base64Url(Signature)` 규격으로 Cookie 헤더에 인코딩 왜곡 없이 안전 전송
- **타이밍 공격(Timing Attack) 방어**: `MessageDigest.isEqual` 상수 시간(Constant-time) 바이트 배열 비교
- **자동 만료 검증(TTL)**: 초 단위 만료 시각이 경과한 쿠키는 자동으로 무효화(`Optional.empty()`)
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리(`javax.crypto.Mac`, `java.security.MessageDigest`, `java.util.Base64`)만 사용

## Usage

```java
import com.msp.sessioncookie.SignedSessionCookie;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

String secretKey = "your-secure-server-side-secret-key";

// 1. 세션 속성 직렬화 및 쿠키 발급 (유효기간 30분)
Map<String, String> sessionAttributes = Map.of(
    "userId", "user-1004",
    "role", "ADMIN",
    "name", "홍길동"
);

String cookieValue = SignedSessionCookie.serialize(
    sessionAttributes,
    secretKey,
    Duration.ofMinutes(30)
);

// HTTP Response Header: Set-Cookie: SESSION=<cookieValue>; HttpOnly; Secure; SameSite=Lax

// 2. HTTP Request 수신 후 쿠키 역직렬화 및 서명/만료 검증
Optional<Map<String, String>> session = SignedSessionCookie.deserialize(cookieValue, secretKey);

if (session.isPresent()) {
    System.out.println("인증 성공!");
    System.out.println("User ID: " + session.get().get("userId"));
    System.out.println("Role: " + session.get().get("role"));
} else {
    System.out.println("위변조되었거나 만료된 유효하지 않은 세션 쿠키입니다.");
}
```
