# 📦 `skill-json-stream-token-splitter`

> **순수 Java 21 표준 라이브러리 기반 대용량 NDJSON 및 거대 JSON 배열 스트리밍 토큰 스플리터**

- **아티팩트 좌표**: `com.msp:skill-json-stream-token-splitter:1.0.0`
- **개별 JAR 산출물**: `modules/skill-json-stream-token-splitter/build/libs/skill-json-stream-token-splitter-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **메모리 효율적인 초경량 스트리밍**:
   - 수 기가바이트(GB) 규모의 NDJSON 및 최상위 JSON 배열(`[ {...}, {...} ]`)을 힙 메모리에 전체 로드하지 않고 요소 단위로 분할 전달
2. **이스케이프 문자 및 중첩 구조 안전 추적**:
   - 문자열 리터럴 내부의 따옴표(`\"`), 백슬래시(`\\`), 중괄호(`{`, `}`) 및 대괄호(`[`, `]`)를 정확히 인식하여 잘못된 조기 분할 방지
   - 다중 중첩 객체 및 배열 depth 완벽 추적
3. **다양한 JSON 타입 지원**:
   - JSON 객체(`{...}`), 배열(`[...]`), 문자열(`"..."`), 숫자(`123`), 불리언(`true`), `null` 스칼라 요소 완벽 처리
4. **간결한 함수형 Consumer API**:
   - `splitNdjson(in, consumer)` / `splitTopLevelArray(in, consumer)`를 통한 람다 스트림 파이프라인 연계

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.jsonstream.JsonStreamSplitter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonStreamExample {
    public static void main(String[] args) throws Exception {
        // 1. 대용량 NDJSON 스트리밍 분할
        try (InputStream in = Files.newInputStream(Path.of("massive-logs.ndjson"))) {
            JsonStreamSplitter.splitNdjson(in, line -> {
                System.out.println("Processing NDJSON entry: " + line);
            });
        }

        // 2. 대용량 JSON 배열 스트리밍 분할
        try (InputStream in = Files.newInputStream(Path.of("giant-array.json"))) {
            JsonStreamSplitter.splitTopLevelArray(in, elementJson -> {
                System.out.println("Processing Array element: " + elementJson);
            });
        }
    }
}
```
