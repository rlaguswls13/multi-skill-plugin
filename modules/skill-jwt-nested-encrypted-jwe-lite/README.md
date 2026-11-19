# 📦 `skill-jwt-nested-encrypted-jwe-lite`

> **RFC 7516 JSON Web Encryption (JWE) RSA-OAEP Key Wrap & A256GCM Content Encryption in Pure Java 21**

- **아티팩트 좌표**: `com.msp:skill-jwt-nested-encrypted-jwe-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-jwt-nested-encrypted-jwe-lite/build/libs/skill-jwt-nested-encrypted-jwe-lite-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21 Standard Cryptography)

---

## 1. 주요 기능 (Features)

1. **RFC 7516 Compact Serialization**:
   - `BASE64URL(ProtectedHeader) . BASE64URL(EncryptedKey) . BASE64URL(IV) . BASE64URL(Ciphertext) . BASE64URL(Tag)`
   - 점(`.`)으로 구분된 5단 구조 표준 암호화 토큰 생성 및 파싱
2. **지원 알고리즘 (Supported Algorithms)**:
   - **키 관리 (`alg`)**:
     - `RSA-OAEP-256` (권장: SHA-256 및 MGF1-SHA-256)
     - `RSA-OAEP` (레거시: SHA-1 및 MGF1-SHA-1)
     - `dir` (사전 공유 대칭키 직접 암호화)
   - **콘텐츠 암호화 (`enc`)**:
     - `A256GCM` (AES-256 GCM 모드, 128비트 인증 태그)
     - `A128GCM` (AES-128 GCM 모드, 128비트 인증 태그)
3. **인증 태그 및 무결성 검증 (Authenticated Encryption)**:
   - AAD(Additional Authenticated Data)로 보호된 헤더의 Base64URL 문자열을 바인딩하여 헤더 위변조 방지
   - 암호문(Ciphertext) 또는 인증 태그(Auth Tag) 위변조 시 즉각 복호화 차단
4. **중첩 JWT (Nested JWT) 지원**:
   - `cty: "JWT"` 헤더 설정으로 서명된 JWS 토큰을 JWE로 중첩 암호화 지원

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.jwelite.JweAlgorithm;
import com.msp.jwelite.JweEncryption;
import com.msp.jwelite.JweKeyUtils;
import com.msp.jwelite.JweToken;
import java.security.KeyPair;

public class JweExample {
    public static void main(String[] args) {
        // 1. RSA 2048 키 쌍 생성 (또는 기존 공개키/개인키 로드)
        KeyPair rsaKeys = JweKeyUtils.generateRsaKeyPair(2048);

        // 2. JWE 암호화 (RSA-OAEP-256 + A256GCM)
        String sensitiveJson = "{\"sub\":\"user-99\",\"role\":\"SUPERUSER\",\"secret\":\"k8s-token\"}";
        JweToken token = JweToken.builder()
                .algorithm(JweAlgorithm.RSA_OAEP_256)
                .encryption(JweEncryption.A256GCM)
                .contentType("JWT")
                .keyId("key-2026-rsa")
                .payload(sensitiveJson)
                .encrypt(rsaKeys.getPublic());

        String compactJwe = token.toCompact();
        System.out.println("Encrypted JWE Compact Token:\n" + compactJwe);

        // 3. JWE 파싱 및 개인키 복호화
        JweToken parsedToken = JweToken.parse(compactJwe);
        String decryptedPayload = parsedToken.decryptToString(rsaKeys.getPrivate());
        System.out.println("Decrypted Payload: " + decryptedPayload);
    }
}
```
