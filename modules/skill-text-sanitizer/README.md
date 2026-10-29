# 📦 `skill-text-sanitizer`

> **외부 라이브러리 없이 순수 Java 21 표준 라이브러리로 개인정보(주민등록번호, 신용카드, 휴대폰, 이메일) 마스킹, XSS 방어(HTML 태그 이스케이프 및 스크립트 제거), 그리고 로그 인젝션(CRLF)을 완벽 차단하는 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-text-sanitizer:1.0.0`
- **개별 JAR 산출물**: `modules/skill-text-sanitizer/build/libs/skill-text-sanitizer-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **`PiiMasker`**:
   - 한국 주민등록번호(RRN): `900101-1234567` ➔ `900101-1******` (성별 자리 보존 또는 전체 마스킹)
   - 신용카드 16자리: `1234-5678-9012-3456` ➔ `1234-****-****-3456` (하이픈/공백 유무 지원)
   - 전화번호/휴대폰: `010-1234-5678` ➔ `010-****-5678`, `02-1234-5678` ➔ `02-****-5678`
   - 이메일 주소: `john.doe@example.com` ➔ `jo******@example.com`
   - `maskAll()`: 문서 내 모든 PII를 한 번에 자동 검출 및 마스킹
2. **`HtmlSanitizer`**:
   - XSS 방어: `<script>`, `<iframe>`, `<object>`, `<svg>` 등의 실행 태그 및 본문 자동 제거
   - 위험 속성 제거: `onload`, `onerror`, `onclick` 등 모든 인라인 이벤트 핸들러 및 `javascript:` 의사 프로토콜 제거
   - HTML 엔티티 이스케이프/언이스케이프 (`&`, `<`, `>`, `"`, `'`)
   - `stripTags()`: 순수 텍스트만 추출
3. **`LogSanitizer`**:
   - 로그 포저리/인젝션(CWE-117) 방어: 개행문자(`\r`, `\n`) 및 비출력 제어문자 제거 또는 치환
   - `escape()`: 줄바꿈을 `\r`, `\n` 리터럴 문자열로 안전하게 변환
4. **`TextSanitizers`**:
   - 모든 기능을 단일 진입점에서 호출 가능한 유연한 통합 퍼사드(Facade)

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.sanitizer.*;

// 1. 개인정보(PII) 마스킹
String document = "홍길동 (주민번호: 950101-1234567, 휴대폰: 010-9876-5432, 메일: hong@company.com)";
String maskedDoc = TextSanitizers.maskAllPii(document);
// 결과: "홍길동 (주민번호: 950101-1******, 휴대폰: 010-****-5432, 메일: ho**@company.com)"

// 2. XSS 방어 및 안전한 HTML 변환
String userBio = "<p>반갑습니다!</p><script>alert('pwned')</script><img src='x' onerror='steal()'>";
String safeHtml = TextSanitizers.sanitizeHtml(userBio);
// 결과: "<p>반갑습니다!</p><img src='x'>"

// 3. 로그 인젝션(CRLF) 방어
String taintedUsername = "attacker\r\n[2026-10-30 ERROR] Fake System Crash!";
String safeLog = TextSanitizers.sanitizeLog(taintedUsername);
// 결과: "attacker_[2026-10-30 ERROR] Fake System Crash!"
```
