# Password Entropy Meter (`skill-password-entropy-meter`)

정보이론 기반의 풀 크기 엔트로피($E = L \times \log_2(R)$) 및 섀넌 엔트로피(Shannon Entropy), 사전 공격 단어 검출, 연속/반복 문자 패턴 페널티를 결합하여 비밀번호 보안 강도를 정확하게 평가하는 순수 Java 21 단일 기술 모듈입니다.

외부 무거운 라이브러리(zxcvbn 등) 없이 표준 라이브러리만으로 빠르고 경량하게 동작합니다.

## 주요 기능

- **정량적 엔트로피 계산**:
  - Pool Size 기반 엔트로피: 소문자(26), 대문자(26), 숫자(10), 특수문자(32) 풀 분류
  - 섀넌 엔트로피(Shannon Entropy): 문자 빈도 분포에 기반한 무작위도 측정
- **취약 패턴 탐지 및 페널티**:
  - 흔히 쓰이는 딕셔너리 비밀번호(e.g. `password`, `123456`, `admin`, `qwerty` 등)
  - 연속된 반복 문자(e.g. `aaaa`, `1111`)
  - 알파벳/숫자 순차열 및 키보드 배열(e.g. `abcdef`, `123456`, `qwerty`, `asdf`)
  - 8자 미만 짧은 패스워드 페널티
- **상세 평가 보고서 (`EntropyReport`)**:
  - 엔트로피 비트수, 5단계 등급(`VERY_WEAK`, `WEAK`, `FAIR`, `STRONG`, `VERY_STRONG`), 감지된 풀 크기, 경고 및 개선 제안 목록 제공

## 사용 예제

```java
import com.msp.pwdentropy.EntropyReport;
import com.msp.pwdentropy.PasswordEntropyMeter;

// 1. 비밀번호 강도 평가
EntropyReport report = PasswordEntropyMeter.evaluate("X9$mK#vL82@p!qZ5");

System.out.println("Entropy: " + report.entropyBits() + " bits"); // ~95+ bits
System.out.println("Strength: " + report.strength());              // VERY_STRONG
System.out.println("Pool size: " + report.poolSize());             // 94
System.out.println("Is Acceptable: " + report.isAcceptable());     // true
System.out.println("Is Strong: " + report.isStrong());             // true

// 2. 취약한 비밀번호 평가
EntropyReport weak = PasswordEntropyMeter.evaluate("password123");
System.out.println("Strength: " + weak.strength());                // VERY_WEAK
System.out.println("Warnings: " + weak.warnings());
// Warnings: [Password contains commonly breached dictionary word/pattern: 'password']
System.out.println("Suggestions: " + weak.suggestions());
```
