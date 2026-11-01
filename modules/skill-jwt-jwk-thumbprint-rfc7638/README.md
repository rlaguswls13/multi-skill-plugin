# 🔑 `skill-jwt-jwk-thumbprint-rfc7638`

> **순수 Java 21 표준 라이브러리 기반 RFC 7638 JSON Web Key (JWK) SHA-256 썸프린트(Thumbprint) 계산기**

- **아티팩트 좌표**: `com.msp:skill-jwt-jwk-thumbprint-rfc7638:1.0.0`
- **개별 JAR 산출물**: `modules/skill-jwt-jwk-thumbprint-rfc7638/build/libs/skill-jwt-jwk-thumbprint-rfc7638-1.0.0.jar`
- **외부 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 특징 (Features)

1. **RFC 7638 공식 규격 완벽 준수**:
   - RFC 7638 Section 3.1 공식 테스트 벡터 완벽 검증
   - RSA 키 정규화: `{"e":"...","kty":"RSA","n":"..."}` (사전순 정렬, 공백 제거)
   - EC 키 정규화: `{"crv":"...","kty":"EC","x":"...","y":"..."}`
   - Base64URL 패딩 없는 인코딩 (`Base64.getUrlEncoder().withoutPadding()`)
2. **다양한 입력 인터페이스 제공**:
   - raw JWK JSON 문자열 입력 (`computeThumbprintFromJwkJson`)
   - Java 표준 `RSAPublicKey` 객체 직접 입력 (`computeRsaThumbprint`)
   - Base64URL 인코딩된 개별 파라미터(`n`, `e`, `crv`, `x`, `y`) 직접 입력
3. **무의존 초경량 JSON 파서 내장**:
   - Jackson이나 Gson 등 무거운 서드파티 JSON 라이브러리 없이 순수 표준 라이브러리로 최상위 JWK 멤버 안전 파싱
4. **암호화 키 식별자(kid) 표준화**:
   - JWK `kid` 필드가 누락되었을 때 RFC 7638 규격 썸프린트로 일관된 고유 ID 생성 가능

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.jwkthumbprint.JwkThumbprintCalculator;

public class JwkThumbprintExample {
    public static void main(String[] args) {
        // 1. JWK JSON 문자열로부터 직접 썸프린트 계산
        String jwkJson = """
                {
                  "kty": "RSA",
                  "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
                  "e": "AQAB",
                  "alg": "RS256",
                  "kid": "2011-04-29"
                }
                """;

        String thumbprint = JwkThumbprintCalculator.computeThumbprintFromJwkJson(jwkJson);
        System.out.println("RFC 7638 Thumbprint: " + thumbprint);
        // 출력: NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs

        // 2. EC 공개키 파라미터로 계산
        String ecThumbprint = JwkThumbprintCalculator.computeEcThumbprint(
                "P-256",
                "f83OJ3D2xFMTbKEBaGizcl76jcDxqJbn8YQChIIwvTk",
                "x_daQauaubbgFGsqaDZTlIGMbQZsK_n9SPTHqOkpmrE"
        );
        System.out.println("EC Thumbprint: " + ecThumbprint);
    }
}
```
