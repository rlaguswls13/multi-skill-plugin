# 📦 `skill-case-converter`

> **외부 라이브러리 없이 순수 Java 21 표준 라이브러리로 문자열 대소문자 표기법(camelCase, snake_case, kebab-case, PascalCase, CONSTANT_CASE, Title Case)을 양방향 상호 변환하고, 연속된 약어(Acronym: "JSONParser" -> "json_parser") 및 복합 식별자를 지능적으로 처리하는 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-case-converter:1.0.0`
- **개별 JAR 산출물**: `modules/skill-case-converter/build/libs/skill-case-converter-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **포괄적인 표기법 변환**:
   - `toCamelCase(str)`: `myVariableName`
   - `toPascalCase(str)`: `MyVariableName`
   - `toSnakeCase(str)`: `my_variable_name`
   - `toKebabCase(str)`: `my-variable-name`
   - `toConstantCase(str)`: `MY_VARIABLE_NAME`
   - `toTitleCase(str)`: `My Variable Name`
2. **연속된 대문자 약어(Acronym) 완벽 분리**:
   - `"JSONParser"` ➔ `"json_parser"`, `"jsonParser"`, `"JsonParser"`, `"JSON_PARSER"`
   - `"parseJSONString"` ➔ `"parse_json_string"`, `"parseJsonString"`
   - `"getHTTPResponseCode"` ➔ `"get_http_response_code"`, `"getHttpResponseCode"`
3. **숫자 및 특수 구분자 복합 식별자 지원**:
   - `"order123Status"` ➔ `"order123_status"`
   - `"utf8String"` ➔ `"utf8_string"`
   - 연속된 언더스코어(`__`), 하이픈(`--`), 공백 등 유연한 정규화
4. **고성능 토크나이저**:
   - 무거운 정규식 백트래킹 없이 단일 순회(O(N)) 상태 기반으로 단어를 분리하여 극대화된 처리 속도 보장.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.textcase.CaseConverter;
import com.msp.textcase.CaseFormat;

public class Example {
    public static void main(String[] args) {
        // 1. 단일 메서드 변환
        String camel = CaseConverter.toCamelCase("user_first_name"); // "userFirstName"
        String snake = CaseConverter.toSnakeCase("createdAtTimestamp"); // "created_at_timestamp"
        String kebab = CaseConverter.toKebabCase("UserProfileSetting"); // "user-profile-setting"
        String constant = CaseConverter.toConstantCase("maxRetryCount"); // "MAX_RETRY_COUNT"
        String title = CaseConverter.toTitleCase("customer_billing_address"); // "Customer Billing Address"

        // 2. 약어(Acronym) 스마트 분리
        String jsonApi = CaseConverter.toSnakeCase("JSONParserService"); // "json_parser_service"
        String parseJson = CaseConverter.toCamelCase("PARSE_JSON_PAYLOAD"); // "parseJsonPayload"

        // 3. Enum 포맷 동적 변환
        String result = CaseConverter.convert("hello-world-again", CaseFormat.PASCAL_CASE); // "HelloWorldAgain"
    }
}
```
