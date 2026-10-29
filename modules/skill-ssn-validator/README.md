# 📦 `skill-ssn-validator`

> **외부 라이브러리 없이 순수 Java 21 표준 라이브러리로 대한민국 주민등록번호 및 외국인등록번호(13자리)의 체크섬 검증, 생년월일/성별/내외국인 메타데이터 추출, 그리고 안전한 마스킹 처리를 제공하는 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-ssn-validator:1.0.0`
- **개별 JAR 산출물**: `modules/skill-ssn-validator/build/libs/skill-ssn-validator-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **`SsnValidator` (주민등록번호 / 외국인등록번호 검증기)**:
   - 13자리 번호(`XXXXXX-XXXXXXX` 또는 `XXXXXXXXXXXXX`) 형식 검증
   - 공식 가중치 체크섬 알고리즘(`[2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5]` Modulo 11) 적용
   - 내국인 및 외국인(5, 6, 7, 8) 체크섬 공식 완벽 구분 지원
   - 세기(1800년대 9,0 / 1900년대 1,2,5,6 / 2000년대 3,4,7,8) 기반 정확한 `LocalDate` 생년월일 유효성 검사 (2월 30일 등 불가능한 날짜 자동 차단)
   - 성별(`MALE`, `FEMALE`), 내외국인 구분, 만 나이 및 세는나이(한국 나이) 자동 산출
2. **`SsnMasker` (개인정보보호 마스킹 유틸리티)**:
   - `mask(ssn)`: 성별 자리 보존 마스킹 (`900101-1******`)
   - `maskFull(ssn)`: 뒷자리 전체 마스킹 (`900101-*******`)
   - `maskBirthOnly(ssn)`: 앞자리(생년월일) 마스킹 (`******-1234567`)
   - `maskCustom(ssn, prefix, suffix, maskChar)`: 가변 커스텀 마스킹

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.ssn.*;
import java.time.LocalDate;

// 1. 유효성 검사 및 상세 정보 파싱
String ssn = "900515-1234569";
SsnInfo info = SsnValidator.validate(ssn);

if (info.valid()) {
    LocalDate birth = info.birthDate(); // 1990-05-15
    Gender gender = info.gender();       // Gender.MALE
    boolean foreigner = info.foreigner(); // false
    int age = info.internationalAge();   // 만 나이
    int koreanAge = info.koreanAge();    // 한국식 나이
}

// 2. 가벼운 메타데이터 추출 (체크섬 미검증 상태에서도 추출 가능)
LocalDate birthDate = SsnValidator.extractBirthDate("950815-1234567").orElse(null);
Gender gender = SsnValidator.extractGender("950815-2234567").orElse(null);

// 3. 개인정보 안전 마스킹
String masked = SsnMasker.mask("900101-1234567");      // "900101-1******"
String fullMask = SsnMasker.maskFull("900101-1234567");  // "900101-*******"
```
