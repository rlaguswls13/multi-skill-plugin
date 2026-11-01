# 📦 `skill-http-auth-bearer-basic-parser`

> **RFC 7235 / RFC 7617 / RFC 6750 HTTP Authorization 헤더 파서 및 포매터 (Basic, Bearer, Custom)**

- **아티팩트 좌표**: `com.msp:skill-http-auth-bearer-basic-parser:1.0.0`
- **개별 JAR 산출물**: `modules/skill-http-auth-bearer-basic-parser/build/libs/skill-http-auth-bearer-basic-parser-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **RFC 7235 / 7617 / 6750 표준 인증 체계 지원**:
   - `Basic`: Base64 디코딩, RFC 7617 사용자명 콜론 검증, 패스워드 내부 콜론(`:`) 안전 보존.
   - `Bearer`: RFC 6750 OAuth 2.0 Bearer 토큰 파싱 및 검증.
   - `Custom`: Digest, Hawk, API-Key 등 커스텀 스킴 지원 및 파라미터(`key="value"`) 자동 파싱.
2. **Java 21 Sealed Interface 및 Record 구조**:
   - `AuthCredentials` sealed 인터페이스 기반으로 패턴 매칭 switch 지원.
   - 불변(Immutable) 레코드 구조 (`BasicAuthCredentials`, `BearerAuthCredentials`, `CustomAuthCredentials`).
3. **양방향 포매팅 및 강력한 예외 처리**:
   - `formatBasic(username, password)`, `formatBearer(token)` 제공.
   - 대소문자 무관 스킴 판별 및 공백 정규화.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.httpauth.*;

public class HttpAuthExample {
    public static void main(String[] args) {
        // 1. Basic 인증 헤더 파싱
        String authHeader = "Basic YWxhZGRpbjpvcGVuIHNlc2FtZQ==";
        AuthCredentials credentials = HttpAuthParser.parse(authHeader);

        if (credentials instanceof BasicAuthCredentials basic) {
            System.out.println("User: " + basic.username());   // aladdin
            System.out.println("Pass: " + basic.password());   // open sesame
        }

        // 2. Bearer 인증 헤더 포매팅 및 파싱
        String bearerHeader = HttpAuthParser.formatBearer("eyJhbGciOi...");
        AuthCredentials parsedBearer = HttpAuthParser.parse(bearerHeader);
        if (parsedBearer instanceof BearerAuthCredentials bearer) {
            System.out.println("Token: " + bearer.token());
        }

        // 3. Java 21 패턴 매칭 switch
        switch (credentials) {
            case BasicAuthCredentials b -> System.out.println("Basic auth for " + b.username());
            case BearerAuthCredentials b -> System.out.println("Bearer token " + b.token());
            case CustomAuthCredentials c -> System.out.println("Custom scheme " + c.scheme());
        }
    }
}
```
