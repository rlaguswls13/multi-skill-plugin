# 🔑 `skill-jwt-cose-key-cbor-lite`

> **순수 Java 21 기반 RFC 8152 / RFC 9052 COSE Key (CBOR Object Signing and Encryption) 디코더 및 Java `PublicKey` 변환 엔진 (WebAuthn, Passkeys, FIDO2 호환)**

- **아티팩트 좌표**: `com.msp:skill-jwt-cose-key-cbor-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-jwt-cose-key-cbor-lite/build/libs/skill-jwt-cose-key-cbor-lite-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **순수 Java 21 CBOR (RFC 8949) 스트리밍 파서**:
   - Jackson, BouncyCastle 등 외부 거대 라이브러리 없이 순수 JDK만으로 CBOR 인코딩/디코딩 완벽 구현
   - 음수 정수, 바이트 스트링, UTF-8 텍스트 스트링, 배열, 맵, 불리언, null 완벽 파싱
2. **WebAuthn / Passkeys / FIDO2 표준 COSE Key 완벽 지원**:
   - `kty`: 2 (EC2), 3 (RSA), 1 (OKP)
   - `alg`: -7 (ES256), -257 (RS256), -8 (EdDSA), -35 (ES384), -36 (ES512)
   - `crv`: 1 (P-256), 2 (P-384), 3 (P-521), 6 (Ed25519), 8 (secp256k1)
3. **표준 `java.security.PublicKey` 즉시 생성**:
   - `key.toPublicKey()` 호출 시 `ECPublicKey`, `RSAPublicKey`, `Ed25519 PublicKey`로 자동 인스턴스화
   - 표준 `java.security.Signature`를 통한 전자서명 검증 즉시 연동 가능
4. **COSE Key 생성 및 CBOR 직렬화(`encode`)**:
   - 공개키 파라미터를 조합하여 WebAuthn 응답이나 JWK-COSE 변환 시 CBOR 바이트 스트림 생성 지원

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.cose.CoseKey;
import java.security.PublicKey;
import java.security.Signature;

public class CoseKeyExample {
    public static void main(String[] args) throws Exception {
        // 1. WebAuthn Passkey 인증 응답에서 수신한 COSE Key CBOR 바이트 파싱
        byte[] authDataCoseKeyBytes = /* WebAuthn attestation / assertion COSE public key bytes */ new byte[]{};

        CoseKey coseKey = CoseKey.parse(authDataCoseKeyBytes);

        System.out.println("Key Type (kty): " + coseKey.getKeyType().orElse(null)); // 2 (EC2)
        System.out.println("Algorithm (alg): " + coseKey.getAlgorithm().orElse(null)); // -7 (ES256)
        System.out.println("Curve (crv): " + coseKey.getCurve().orElse(null)); // 1 (P-256)

        // 2. 표준 java.security.PublicKey로 변환
        PublicKey publicKey = coseKey.toPublicKey();

        // 3. 전자서명 검증
        byte[] clientDataHash = "user_signature_payload".getBytes();
        byte[] signature = /* client signature */;

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update(clientDataHash);

        boolean isValid = verifier.verify(signature);
        System.out.println("Passkey signature valid: " + isValid);
    }
}
```
