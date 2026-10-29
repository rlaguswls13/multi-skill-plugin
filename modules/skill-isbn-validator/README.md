# 📦 `skill-isbn-validator`

> **외부 라이브러리 없이 순수 Java 21 표준 라이브러리로 국제 표준도서번호(ISBN-10 및 ISBN-13)의 체크섬 검증, 체크디지트('X' 포함) 계산, 상호 포맷 변환 및 서식화를 제공하는 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-isbn-validator:1.0.0`
- **개별 JAR 산출물**: `modules/skill-isbn-validator/build/libs/skill-isbn-validator-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **`IsbnValidator` (ISBN 검증 및 상호 변환기)**:
   - **ISBN-10**: 가중치 Modulo 11 알고리즘 (`X` 또는 `x` 체크디지트 포함 완벽 지원)
   - **ISBN-13**: Bookland(`978`, `979`) 접두사 및 가중치 Modulo 10 알고리즘 완벽 지원
   - `isValid(isbn)`: 10자리 / 13자리 자동 감지 유효성 검증
   - `convertIsbn10To13(isbn10)`: 10자리 ISBN을 978 접두사 13자리 ISBN으로 재계산 변환
   - `convertIsbn13To10(isbn13)`: 978 접두사 13자리 ISBN을 10자리로 역변환 (`979`는 변환 불가능 판정)
   - `calculateCheckDigit10`, `calculateCheckDigit13`: 체크디지트 독립 계산 유틸리티
2. **`IsbnFormatter`**:
   - `clean(isbn)`: 공백, 하이픈 제거 및 소문자 'x' 대문자 'X' 정규화
   - `formatIsbn10(isbn)`, `formatIsbn13(isbn)`: 표준 하이픈 서식 출력

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.isbn.*;

// 1. ISBN-10 및 ISBN-13 유효성 검증
boolean v10 = IsbnValidator.isValid("0-306-40615-2");   // true
boolean vX  = IsbnValidator.isValid("0-8044-2957-X");   // true ('X' 체크디지트)
boolean v13 = IsbnValidator.isValid("978-0-306-40615-7"); // true

// 2. ISBN-10 ➔ ISBN-13 변환
String isbn13 = IsbnValidator.convertIsbn10To13("0-306-40615-2");
// 결과: "9780306406157"

// 3. ISBN-13 ➔ ISBN-10 역변환
String isbn10 = IsbnValidator.convertIsbn13To10("978-0-306-40615-7").orElseThrow();
// 결과: "0306406152"

// 4. 상세 메타데이터 파싱
IsbnInfo info = IsbnValidator.validate("0-306-40615-2");
System.out.println("Type: " + info.type());     // ISBN_10
System.out.println("Clean: " + info.clean());   // "0306406152"
System.out.println("13-digit: " + info.isbn13()); // "9780306406157"
```
