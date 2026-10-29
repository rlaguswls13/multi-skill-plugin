# 📦 `skill-json-path-lite`

> **Jayway JsonPath 등의 무거운 외부 종속성 없이, 순수 Java 21 표준 라이브러리만으로 중첩 객체(Map, List, Java Record, POJO) 및 JSON 문자열에 대한 깊은 점 표기법(Dot-notation) 탐색과 타입 안전 추출을 제공하는 초경량 JSONPath 라이브러리**

- **아티팩트 좌표**: `com.msp:skill-json-path-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-json-path-lite/build/libs/skill-json-path-lite-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **`JsonPath` & `JsonPaths`**:
   - 점 표기법(Dot-notation): `user.orders[0].items[1].name`
   - 배열 인덱싱: `[0]`, `[1]`, 음수 인덱스(`[-1]`로 마지막 원소 접근)
   - 특수 키 지원: `['special-key']`, `["key.with.dot"]`
   - 길이 및 크기 프로퍼티: `items.length`, `items.size`
2. **다양한 대상 지원**:
   - `Map<String, Object>` 및 `List<Object>`
   - Java 21 `record` 및 일반 자바빈(POJO)
   - 원시 JSON 문자열 (`String json` 직접 질의)
3. **타입 안전 추출 메서드**:
   - `getString(root, path)`, `getInt(...)`, `getLong(...)`, `getDouble(...)`, `getBool(...)`
   - `getList(...)`, `getMap(...)`
   - `exists(root, path)`
4. **내장 경량 JSON 파서 (`MiniJson`)**:
   - 외부 Jackson, Gson 없이 순수 Java로 JSON RFC 8259 완벽 지원

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.jsonpath.*;

String json = """
    {
      "store": {
        "book": [
          {"category": "reference", "author": "Nigel", "title": "Sayings", "price": 8.95},
          {"category": "fiction", "author": "Evelyn", "title": "Sword", "price": 12.99}
        ]
      }
    }
    """;

// 1. 깊은 경로 탐색
Optional<String> firstTitle = JsonPaths.getString(json, "store.book[0].title");
// Optional["Sayings"]

Optional<Double> lastPrice = JsonPaths.getDouble(json, "store.book[-1].price");
// Optional[12.99]

// 2. 배열 길이 확인
Optional<Integer> bookCount = JsonPaths.getInt(json, "store.book.length");
// Optional[2]

// 3. Java Record 및 Map 객체 직접 탐색
record User(String name, List<String> hobbies) {}
User user = new User("Alice", List.of("Reading", "Gaming"));

Optional<String> firstHobby = JsonPaths.getString(user, "hobbies[0]");
// Optional["Reading"]
```
