# 📦 `skill-yaml-lite`

> **SnakeYAML 등 무거운 서드파티 라이브러리 없이, 순수 Java 21 표준 라이브러리만으로 동작하는 초경량 YAML 파서 및 직렬화 유틸리티**

- **아티팩트 좌표**: `com.msp:skill-yaml-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-yaml-lite/build/libs/skill-yaml-lite-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **`YamlParser.parse(String)`**:
   - YAML 문자열을 `Map<String, Object>` 계층 구조로 즉시 파싱.
   - 키-값 쌍(`key: value`), 들여쓰기 기반 중첩 매핑(`Map`), 리스트(`- item`), 복합 리스트(`- key: val`) 완벽 지원.
   - 불리언(`true`/`false`/`yes`/`no`), 정수, 실수, `null`(`~`), 따옴표 감싸기(`"..."`, `'...'`) 및 이스케이프 지원.
   - 블록 스칼라 지원: 리터럴(`|`), 접힘(`>`).
   - 인라인 플로우 문법 지원: `[a, b, c]`, `{x: 1, y: 2}`.
   - `#` 주석 제거(라인 주석 및 인라인 주석 지원, 따옴표 내부 주석 보호).

2. **`YamlWriter.dump(Map)`**:
   - `Map<String, ?>`을 표준 YAML 규격의 포맷 문자열로 변환.
   - 중첩 맵, 리스트, 멀티라인 텍스트(`|`), 특수문자 자동 이스케이프 및 따옴표 처리.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.yaml.YamlParser;
import com.msp.yaml.YamlWriter;

import java.util.List;
import java.util.Map;

public class YamlExample {
    public static void main(String[] args) {
        String yaml = """
            server:
              host: 127.0.0.1
              port: 8080
              tags: [web, api, prod]
            users:
              - id: 1
                name: Alice
              - id: 2
                name: Bob
            description: |
              Hello World
              Multi-line text
            """;

        // 1. YAML 파싱
        Map<String, Object> config = YamlParser.parse(yaml);
        System.out.println("Host: " + ((Map<?, ?>) config.get("server")).get("host"));
        System.out.println("Users: " + config.get("users"));

        // 2. YAML 직렬화
        Map<String, Object> data = Map.of(
            "appName", "sample-app",
            "version", "1.0.0",
            "profiles", List.of("dev", "stage")
        );
        String output = YamlWriter.dump(data);
        System.out.println(output);
    }
}
```
