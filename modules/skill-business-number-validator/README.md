# 📦 `skill-business-number-validator`

> **외부 라이브러리 없이 순수 Java 21 표준 라이브러리로 대한민국 사업자등록번호(10자리) 및 법인등록번호(13자리)의 체크섬 검증, 서식화, 법인/사업자 유형 식별을 제공하는 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-business-number-validator:1.0.0`
- **개별 JAR 산출물**: `modules/skill-business-number-validator/build/libs/skill-business-number-validator-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **`BizNumberValidator` (사업자등록번호 검증기)**:
   - 10자리 번호(`XXX-XX-XXXXX` 또는 `XXXXXXXXXX`) 체크섬 검증
   - 국세청 공식 가중치 알고리즘(`[1, 3, 7, 1, 3, 7, 1, 3, 5]` Modulo 10) 적용
   - 사업자 분류 식별 (`INDIVIDUAL_TAXABLE`, `INDIVIDUAL_TAX_EXEMPT`, `PROFIT_CORP`, `NON_PROFIT`, `FOREIGN_CORP`, `PUBLIC_INSTITUTION`)
   - 체크디지트 계산 및 포맷팅/언포맷팅 유틸리티
2. **`CorpNumberValidator` (법인등록번호 검증기)**:
   - 13자리 번호(`XXXXXX-XXXXXXX` 또는 `XXXXXXXXXXXXX`) 체크섬 검증
   - 대법원 등기 공식 가중치 알고리즘(`[1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2]` Modulo 10) 적용
   - 법인 종류 식별 (`CorporateType`: 주식회사, 유한회사, 합명회사, 사단법인, 재단법인, 학교법인, 의료법인 등)
   - 관할 등기소 코드(앞 4자리) 파싱
3. **`KoreanRegistrationValidators`**:
   - 사업자등록번호와 법인등록번호를 한 곳에서 검증할 수 있는 통합 퍼사드

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.biznum.*;

// 1. 사업자등록번호 체크섬 검증
boolean isValid = BizNumberValidator.isValid("120-81-47521"); // true (삼성전자)

// 2. 사업자등록번호 상세 검증 및 사업자 유형 분석
BizNumberValidator.Result bizResult = BizNumberValidator.validate("120-81-47521");
if (bizResult.valid()) {
    System.out.println("포맷팅 번호: " + bizResult.formatted()); // "120-81-47521"
    System.out.println("사업자 구분: " + bizResult.bizType().getKoreanDescription()); // "영리법인"
}

// 3. 법인등록번호 체크섬 검증 및 법인 형태 식별
String corpNum = "110111-1234569";
CorpNumberValidator.Result corpResult = CorpNumberValidator.validate(corpNum);
if (corpResult.valid()) {
    System.out.println("법인 형태: " + corpResult.corporateType().getKoreanDescription()); // "주식회사"
    System.out.println("관할 등기소 코드: " + corpResult.registryOfficeCode()); // "1101"
}

// 4. 통합 퍼사드 활용
boolean isCorpValid = KoreanRegistrationValidators.isValidCorpNumber("110111-1234569");
```
