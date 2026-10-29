# 🔐 skill-crypto-lite

초경량 암호화/해싱 유틸리티 (Java 21 순수 표준 라이브러리 `javax.crypto` & `java.security`, 외부 의존성 Zero).

AES-256-GCM 인증 대칭 암호화, PBKDF2 키 유도, HMAC-SHA256 디지털 서명(타이밍 공격 방어), SHA-256/512 해싱 기능을 제공합니다.

---

## 🚀 주요 기능

1. **AesGcmCrypto (AES-256-GCM)**:
   - 암호화마다 12바이트 난수 IV(`SecureRandom`) 자동 생성.
   - 128비트 무결성 인증 태그(Auth Tag) 결합으로 데이터 위변조 즉각 감지.
   - 페이로드 구조: `[12-byte IV][Ciphertext + 16-byte GCM Tag]`.
   - AAD (추가 인증 데이터, Additional Authenticated Data) 완벽 지원.

2. **Pbkdf2KeyDerivation (PBKDF2WithHmacSHA256)**:
   - 사용자 패스워드로부터 256비트 AES 암호화 키 안전하게 도출.
   - 기본 60,000회 반복(Iteration), 16바이트 솔트(Salt) 난수 생성.

3. **HmacSigner (HMAC-SHA256)**:
   - API 요청/응답 변조 방지를 위한 고속 HMAC-SHA256 서명.
   - `MessageDigest.isEqual` 기반 상수 시간(Constant-time) 검증으로 타이밍 공격 방어.

4. **HashUtil**:
   - SHA-256, SHA-512 다이제스트 및 Java 21 `HexFormat` 기반 고속 16진수 변환.

5. **CryptoLite (Unified Facade)**:
   - 모든 암호화/서명/해싱 작업을 간결하게 처리하는 통합 파사드.

---

## 💻 사용 예제

### 1. AES-256-GCM 암복호화

```java
import com.msp.crypto.CryptoLite;
import javax.crypto.SecretKey;

// 256비트 키 생성
SecretKey key = CryptoLite.generateAesKey();

// 문자열 암복호화 (Base64 출력)
String ciphertext = CryptoLite.encryptAesGcm("민감한 데이터", key);
String decrypted = CryptoLite.decryptAesGcm(ciphertext, key);

// AAD (컨텍스트 검증 데이터) 결합 암호화
String aad = "user-id-12345";
String secureCipher = CryptoLite.encryptAesGcm("계좌번호: 110-123-456789", key, aad);
String original = CryptoLite.decryptAesGcm(secureCipher, key, aad);
```

### 2. 패스워드 기반 키 유도 (PBKDF2)

```java
import com.msp.crypto.CryptoLite;
import javax.crypto.SecretKey;

byte[] salt = CryptoLite.generateSalt(16);
SecretKey aesKey = CryptoLite.deriveAesKey("MyUserMasterPassword!@#", salt);

// 유도된 키로 AES 암호화 수행
String encrypted = CryptoLite.encryptAesGcm("Hello World", aesKey);
```

### 3. HMAC-SHA256 서명 및 검증

```java
import com.msp.crypto.CryptoLite;

String payload = "orderId=9901&amount=45000";
String secretKey = "api-secret-key";

// 서명 생성 (Hex)
String signature = CryptoLite.signHmacHex(payload, secretKey);

// 서명 검증 (상수 시간 비교)
boolean isValid = CryptoLite.verifyHmacHex(payload, signature, secretKey);
```

### 4. SHA-256 / SHA-512 해시

```java
import com.msp.crypto.CryptoLite;

String sha256 = CryptoLite.sha256Hex("hello world");
String sha512 = CryptoLite.sha512Hex("hello world");
```

---

## 🧪 빌드 및 테스트

```bash
.\gradlew.bat :modules:skill-crypto-lite:test
```
