# 🔑 skill-password-hasher

PBKDF2-HMAC-SHA256 기반 보안 비밀번호 단방향 해싱, 상수 시간 검증 및 복잡도(강도) 검증기 (Java 21 순수 표준 라이브러리, 의존성 Zero).

OWASP 권장 65,536회 반복 해싱, 128비트 암호화 난수 Salt, 타이밍 공격 방지 `MessageDigest.isEqual` 검증, 사전식 강도 측정기 및 안전한 랜덤 비밀번호 생성기를 제공합니다.

---

## 🚀 주요 기능

1. **Pbkdf2PasswordHasher**:
   - 알고리즘: `PBKDF2WithHmacSHA256`.
   - 해시 형식: `$pbkdf2$<iterations>$<salt_base64>$<hash_base64>` (표준 Modular Crypt Format 스타일).
   - 기본 65,536회 반복, 16바이트 솔트, 256비트 해시.
   - 상수 시간(Constant-time) 동등성 검사로 타이밍 부채널 공격(Timing Attacks) 원천 차단.
2. **PasswordStrengthValidator**:
   - 길이(최소/최대), 대문자, 소문자, 숫자, 특수문자, 공백 검사.
   - 점수(0~100) 및 강도 레벨(`VERY_WEAK` ~ `VERY_STRONG`) 산출.
   - 프리셋 지원: `standard()`, `strong()`, `lenient()`.
3. **PasswordUtil (Unified Facade)**:
   - `hash()`, `verify()`, `validateStrength()`, `isStrong()`, `generateRandomPassword()`.

---

## 💻 사용 예제

### 1. 비밀번호 해싱 및 검증

```java
import com.msp.pwd.PasswordUtil;

String rawPassword = "MySecurePassword123!@#";

// 1. 단방향 해싱 ($pbkdf2$65536$<salt>$<hash>)
String hash = PasswordUtil.hash(rawPassword);

// 2. 일치 검증 (상수 시간 비교)
boolean matches = PasswordUtil.verify("MySecurePassword123!@#", hash); // true
boolean wrong = PasswordUtil.verify("WrongPassword", hash);             // false
```

### 2. 비밀번호 복잡도(강도) 검증

```java
import com.msp.pwd.PasswordStrengthValidator;
import com.msp.pwd.PasswordStrengthResult;

PasswordStrengthValidator validator = PasswordStrengthValidator.strong();
PasswordStrengthResult result = validator.validate("weakpass");

if (!result.isValid()) {
    System.out.println("강도: " + result.level()); // VERY_WEAK
    System.out.println("점수: " + result.score());
    for (String violation : result.violations()) {
        System.out.println("- " + violation);
    }
}
```

### 3. 암호학적으로 안전한 임시 비밀번호 생성

```java
import com.msp.pwd.PasswordUtil;

// 16자리 특수문자 포함 임시 비밀번호 생성
String tempPassword = PasswordUtil.generateRandomPassword(16, true);
System.out.println("Generated: " + tempPassword);
```

---

## 🧪 빌드 및 테스트

```bash
.\gradlew.bat :modules:skill-password-hasher:test
```
