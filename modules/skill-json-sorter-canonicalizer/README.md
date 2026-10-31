# 📦 `skill-json-sorter-canonicalizer`

> **순수 Java 21 표준 라이브러리 기반 RFC 8785 JCS (JSON Canonicalization Scheme) 정규화기 및 SHA-256 해시 생성기**

- **아티팩트 좌표**: `com.msp:skill-json-sorter-canonicalizer:1.0.0`
- **개별 JAR 산출물**: `modules/skill-json-sorter-canonicalizer/build/libs/skill-json-sorter-canonicalizer-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21, Jackson/Gson 불필요)

---

## 1. 주요 기능 (Features)

1. **RFC 8785 (JCS) 표준 준수**:
   - 객체 키의 UTF-16 코드 유닛 오름차순(Lexicographical) 사전식 정렬
   - 토큰 간 불필요한 공백/줄바꿈/들여쓰기 완전 제거
   - RFC 8785 표준 문자열 이스케이프 (`\"`, `\\`, U+0000..U+001F 제어문자만 이스케이프, 한글/이모지 등 UTF-8 문자 원형 보존)
   - 수치 정규화 (정수형 표현 시 불필요한 소수점 제거, -0의 0 단일화)
2. **독립 경량 내장 파서 (Zero-Dependency)**:
   - 외부 무거운 JSON 라이브러리(Jackson, Gson 등) 없이 표준 Java만으로 중첩 객체 및 배열 구문 분석
3. **디지털 서명 및 콘텐츠 해시 검증 편의 기능**:
   - `sha256Hex(String json)` 메서드를 통해 동일한 데이터에 대해 키 순서나 공백에 무관한 결정론적(Deterministic) 64자리 SHA-256 해시 즉시 산출

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.jsoncanonical.JsonCanonicalizer;
import java.util.Map;

public class JsonCanonicalExample {
    public static void main(String[] args) {
        String unformattedJson = """
            {
               "zebra": "animal",
               "price": 100.0,
               "apple": "fruit",
               "tags": [ "red", "sweet" ]
            }
            """;

        // 1. JSON 문자열 정규화 (키 정렬 및 공백 제거)
        String canonical = JsonCanonicalizer.canonicalize(unformattedJson);
        System.out.println(canonical);
        // {"apple":"fruit","price":100,"tags":["red","sweet"],"zebra":"animal"}

        // 2. Map 구조 직접 정규화
        Map<String, Object> map = Map.of("b", 2, "a", 1);
        System.out.println(JsonCanonicalizer.canonicalize(map));
        // {"a":1,"b":2}

        // 3. 결정론적 SHA-256 다이제스트 산출
        String hash = JsonCanonicalizer.sha256Hex(unformattedJson);
        System.out.println("SHA-256: " + hash);
    }
}
```
