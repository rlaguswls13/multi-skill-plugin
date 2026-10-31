# 📦 `skill-json-masker-selective`

> **순수 Java 21 표준 라이브러리 기반 선별적 JSON 필드 마스킹 및 JSONPath 와일드카드 마스커**

- **아티팩트 좌표**: `com.msp:skill-json-masker-selective:1.0.0`
- **개별 JAR 산출물**: `modules/skill-json-masker-selective/build/libs/skill-json-masker-selective-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **선별적 JSON 필드 마스킹 (Selective Field Masking)**:
   - JSONPath 계층 경로 및 와일드카드(`*`, `[*]`) 기반 타겟 필드 지정
   - 정확한 경로(`"password"`, `"user.password"`) 및 와일드카드 경로(`"*.ssn"`, `"users[*].email"`) 지원
2. **다양한 마스킹 전략 (`MaskingStrategy`)**:
   - `FULL`: 전체 마스킹 (`***`)
   - `PARTIAL_CARD`: 카드번호 앞 4자리, 뒷 4자리 보존 마스킹 (`4111-****-****-1111`)
   - `PARTIAL_EMAIL`: 이메일 로컬 첫 글자 및 도메인 보존 마스킹 (`j***@example.com`)
   - `custom(replacement)`: 사용자 정의 마스킹 문구 (`[REDACTED]`, `###`)
3. **무오류 무중단 JSON 패스스루 (Fail-Safe Pass-Through)**:
   - 잘못된 형식(Malformed)의 JSON이 유입되어도 예외를 던지지 않고 원본 문자열을 안전하게 반환
   - 숫자, 불리언, `null` 및 비대상 필드의 데이터 타입과 구조 완벽 보존
4. **제로 런타임 의존성**:
   - Jackson, Gson, Jayway JsonPath 등 무거운 외부 라이브러리 없이 순수 Java 21만으로 내장 고속 파서 동작

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.jsonmasker.JsonMasker;
import com.msp.jsonmasker.MaskingStrategy;

public class JsonMaskerExample {
    public static void main(String[] args) {
        // 1. 마스커 인스턴스 생성 및 규칙 등록
        JsonMasker masker = JsonMasker.create()
            .withMask("user.password", MaskingStrategy.FULL)
            .withMask("*.cardNumber", MaskingStrategy.PARTIAL_CARD)
            .withMask("members[*].email", MaskingStrategy.PARTIAL_EMAIL)
            .withMask("secretKey", MaskingStrategy.custom("[CONFIDENTIAL]"));

        // 2. 민감 정보가 포함된 원본 JSON
        String rawJson = """
            {
              "user": {
                "name": "Alice",
                "password": "mySuperSecretPassword"
              },
              "billing": {
                "cardNumber": "4111-2222-3333-1111"
              },
              "members": [
                {"email": "john.doe@example.com"},
                {"email": "admin@company.org"}
              ],
              "secretKey": "live_sk_123456789"
            }
            """;

        // 3. 마스킹 실행
        String maskedJson = masker.mask(rawJson);
        System.out.println(maskedJson);
    }
}
```
