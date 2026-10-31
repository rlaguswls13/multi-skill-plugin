# skill-jwt-rs256-pkcs8-loader

외부 서드파티 라이브러리(BouncyCastle 등) 없이 JDK 21 표준 라이브러리와 순수 ASN.1 DER 디코더만으로 PKCS#8 및 PKCS#1 PEM 포맷의 RSA Private/Public 키를 파싱하고 생성하는 경량 보안 모듈입니다.

RS256 JWT 토큰 서명 및 검증, mTLS 인증서 키 로딩 등 PEM 문자열을 RSA `PrivateKey` / `PublicKey` 객체로 즉시 변환해야 하는 환경에 최적화되어 있습니다.

---

## 🚀 주요 기능

- **PKCS#8 Private Key 지원**: `-----BEGIN PRIVATE KEY-----` 헤더 파싱 (`PKCS8EncodedKeySpec`)
- **PKCS#1 RSA Private Key 지원**: `-----BEGIN RSA PRIVATE KEY-----` 헤더의 ASN.1 SEQUENCE 9개 정수(Modulus, Exponents, CRT Primes, Coefficient) 순수 파싱 (`RSAPrivateCrtKeySpec`)
- **X.509 & PKCS#1 Public Key 지원**: `-----BEGIN PUBLIC KEY-----` 및 `-----BEGIN RSA PUBLIC KEY-----` 파싱
- **키 쌍 통합 로더**: `PemKeyLoader.parseKeyPair(privatePem, publicPem)`
- **PEM 직렬화**: `PrivateKey`, `PublicKey` 객체를 64자 개행 PEM 포맷으로 상호 변환
- **Zero-Dependency**: Bouncy Castle 등 무거운 의존성 없는 100% JDK 표준 규격

---

## 📦 사용 예시

```java
import com.msp.pkcs8.PemKeyLoader;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

public class PemLoaderExample {
    public static void main(String[] args) throws Exception {
        String privatePem = """
                -----BEGIN PRIVATE KEY-----
                MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC...
                -----END PRIVATE KEY-----
                """;

        String publicPem = """
                -----BEGIN PUBLIC KEY-----
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
                -----END PUBLIC KEY-----
                """;

        // 1. PEM 문자열에서 RSA 키 파싱
        PrivateKey privateKey = PemKeyLoader.parsePrivateKey(privatePem);
        PublicKey publicKey = PemKeyLoader.parsePublicKey(publicPem);

        // 2. RS256 JWT 페이로드 서명
        byte[] payload = "{\"sub\":\"12345\",\"name\":\"Alice\"}".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(payload);
        byte[] signature = signer.sign();

        // 3. 서명 검증
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(payload);
        boolean isValid = verifier.verify(signature);
        System.out.println("Signature valid: " + isValid);
    }
}
```

---

## 🧪 테스트 검증 매트릭스

- `testPkcs8PrivateKeyRoundtrip`: PKCS#8 PEM 내보내기 및 재파싱 무결성 검증
- `testX509PublicKeyRoundtrip`: X.509 PEM 내보내기 및 재파싱 무결성 검증
- `testPkcs1PrivateKeyRoundtrip`: PKCS#1 ASN.1 DER 인코딩/디코딩 및 CRT 스펙 일치 검증
- `testSignAndVerifyWithParsedKeys`: 로드된 Private/Public 키로 SHA256withRSA 디지털 서명 및 위변조 검증
- `testParseKeyPair`: 키 쌍 동시 로딩 검증
- `testInvalidPemHandling`: Null, 공백, Base64 손상, 미지원 헤더, ASN.1 바이트 깨짐 예외 처리 검증
