# 📦 `skill-jwt-dpop-proof-validator`

> **순수 Java 21 표준 라이브러리 기반 RFC 9449 OAuth 2.0 DPoP (Demonstrating Proof-of-Possession) Proof JWT 검증기**

- **아티팩트 좌표**: `com.msp:skill-jwt-dpop-proof-validator:1.0.0`
- **개별 JAR 산출물**: `modules/skill-jwt-dpop-proof-validator/build/libs/skill-jwt-dpop-proof-validator-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **RFC 9449 완전 준수 (DPoP Proof Verification)**:
   - Header 검증: `typ`은 반드시 `dpop+jwt`여야 함.
   - 알고리즘 지원: `RS256` (RSA), `ES256` (ECDSA P-256).
   - JWK 공개키 검증: 헤더에 포함된 `jwk` 파싱 및 개인키 파라미터 누출(`d`, `p`, `q` 등) 차단.
2. **페이로드 클레임 검증**:
   - `jti`: 고유 토큰 식별자 추출 및 인메모리 `ReplayCache`를 통한 재생 공격(Replay Attack) 방지.
   - `htm`: 요청 HTTP 메서드 일치 검증 (대소문자 무관).
   - `htu`: 요청 HTTP URI 정규화 및 일치 검증 (쿼리 스트링 및 프래그먼트 자동 제거).
   - `iat`: 토큰 발행 시각 및 클록 스큐(Clock Skew, 기본 5분) 검증.
   - `ath`: 액세스 토큰 바인딩 해시(`Base64URL(SHA-256(access_token))`) 검증.
3. **디지털 서명 암호학적 검증**:
   - 순수 Java 21 `java.security` 기반 RS256 / ES256 (IEEE P1363 및 ASN.1 DER 자동 트랜스코딩).
   - 무거운 외부 라이브러리(Nimbus JOSE, BouncyCastle, Jackson) 제로 의존성.
4. **RFC 7638 JWK Thumbprint 산출**:
   - 클라이언트 공개키 지문(SHA-256 base64url) 자동 계산 (`result.thumbprint()`).

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.dpop.*;
import java.time.Duration;

public class Example {
    public static void main(String[] args) {
        // 1. DPoP 검증기 생성 (기본 5분 클록 스큐, ES256/RS256 지원)
        DpopProofValidator validator = new DpopProofValidator();

        String dpopProof = "eyJ0eXAiOiJkcG9wK2p3dCIsImFsZyI6IkVTMjU2IiwiandrIjp7...}.{...}.{...}";
        String httpMethod = "POST";
        String httpUri = "https://api.example.com/payments/transfer";
        String accessToken = "mF_9.B5f-4.1JqM";

        // 2. Proof 검증 및 액세스 토큰 바인딩(ath) 확인
        DpopValidationResult result = validator.validate(
                dpopProof,
                httpMethod,
                httpUri,
                accessToken
        );

        if (result.isValid()) {
            System.out.println("DPoP proof valid!");
            System.out.println("Client JWK Thumbprint: " + result.thumbprint());
            System.out.println("Proof JTI: " + result.claims().jti());
        } else {
            System.err.println("DPoP proof rejected: " + result.failureReason());
        }
    }
}
```
