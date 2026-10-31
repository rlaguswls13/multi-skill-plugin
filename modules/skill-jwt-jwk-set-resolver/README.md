# skill-jwt-jwk-set-resolver

RFC 7517 JSON Web Key Set (JWKS) dynamic parser and key cache resolver with pure Java 21 standard library (zero 3rd-party dependencies).

## 주요 기능
- **RFC 7517 / RFC 7518 규격 완벽 준수**: JSON Web Key Set (`{"keys": [...]}`) 파싱 및 `JwkKey` 변환
- **RSA & EC 공개키 지원**: Base64URL 인코딩된 modulus `n`과 exponent `e`를 pure Java `RSAPublicKeySpec`으로 변환하며, `ECPublicKey` (P-256) 변환 지원
- **TTL 캐시 만료 관리**: 지정된 유효 기간(`cacheTtl`) 동안 키를 메모리 캐싱하고 만료 시 안전하게 보호
- **Zero Runtime Dependencies**: Jackson, Gson, Nimbus 등 무거운 외부 의존성 없이 표준 Java 라이브러리만으로 동작

## 사용 예제

```java
import com.msp.jwk.JwkSetResolver;
import java.security.PublicKey;
import java.time.Duration;

public class Example {
    public static void main(String[] args) {
        String jwksJson = """
            {
              "keys": [
                {
                  "kty": "RSA",
                  "use": "sig",
                  "alg": "RS256",
                  "kid": "my-key-id-1",
                  "n": "u1II2w...",
                  "e": "AQAB"
                }
              ]
            }
            """;

        // 1시간 TTL 캐시 리졸버 생성
        JwkSetResolver resolver = new JwkSetResolver(Duration.ofHours(1));
        resolver.loadJwkSetJson(jwksJson);

        // kid로 RSA PublicKey 조회
        PublicKey publicKey = resolver.resolveKey("my-key-id-1");

        // JWT 서명 검증에 바로 사용 가능
    }
}
```
