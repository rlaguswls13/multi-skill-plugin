# 📦 `skill-json-minifier-prettifier`

> **외부 라이브러리(Jackson, Gson 등) 없이 문자열 리터럴과 이스케이프 시퀀스를 안전하게 보존하며, 커스텀 인덴트 프리티 프린팅과 공백 최소화(Minify)를 수행하는 고성능 순수 Java 21 JSON 포매터**

- **아티팩트 좌표**: `com.msp:skill-json-minifier-prettifier:1.0.0`
- **개별 JAR 산출물**: `modules/skill-json-minifier-prettifier/build/libs/skill-json-minifier-prettifier-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **가독성 높은 프리티 프린팅 (`prettyPrint`)**:
   - 기본 2스페이스 들여쓰기, N칸 들여쓰기(`prettyPrint(json, 4)`), 탭 들여쓰기(`prettyPrint(json, "\t")`) 지원
   - 빈 객체(`{}`) 및 빈 배열(`[]`)은 불필요한 줄바꿈 없이 한 줄로 깔끔하게 처리
2. **초고속 압축 미니파이어 (`minify`)**:
   - 문자열 리터럴 밖의 모든 불필요한 공백/줄바꿈/탭을 제거하여 패킷/저장공간 최적화
3. **문자열 리터럴 완벽 보존 (Verbatim String Integrity)**:
   - 문자열 내부에 포함된 콜론(`:`), 콤마(`,`), 중괄호(`{}`), 대괄호(`[]`), 공백, 이스케이프 따옴표(`\"`), 백슬래시(`\\`)를 절대 훼손하지 않음
4. **구조 검증 및 에러 감지 (`isValid`, `JsonParseException`)**:
   - 닫히지 않은 따옴표, 짝이 맞지 않는 괄호(`{]`, `[}`), 누락된 괄호 자동 검증

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.jsonformat.*;

String compactJson = "{\"title\":\"MSP\",\"tags\":[\"Java\",\"Plugin\"],\"options\":{\"active\":true}}";

// 1. 기본 2-space 프리티 프린트
String pretty = JsonFormat.prettyPrint(compactJson);
System.out.println(pretty);
/*
{
  "title": "MSP",
  "tags": [
    "Java",
    "Plugin"
  ],
  "options": {
    "active": true
  }
}
*/

// 2. 4-space 커스텀 인덴트
String pretty4 = JsonFormat.prettyPrint(compactJson, 4);

// 3. 미니파이 (공백 제거)
String minified = JsonFormat.minify(pretty);
System.out.println(minified);
// {"title":"MSP","tags":["Java","Plugin"],"options":{"active":true}}

// 4. 유효성 검사
boolean valid = JsonFormat.isValid(compactJson); // true
```
