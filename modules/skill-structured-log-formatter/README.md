# 📦 `skill-structured-log-formatter`

> **순수 Java 21 표준 라이브러리 기반 고성능 구조화 JSON 로그 포맷터 (NDJSON, 스택트레이스 배열화, 컨텍스트 맵)**

- **아티팩트 좌표**: `com.msp:skill-structured-log-formatter:1.0.0`
- **개별 JAR 산출물**: `modules/skill-structured-log-formatter/build/libs/skill-structured-log-formatter-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **표준 로깅 스키마 준수**:
   - `@timestamp` (ISO-8601 UTC Instant)
   - `level` (TRACE, DEBUG, INFO, WARN, ERROR)
   - `logger` (패키지/클래스명)
   - `thread` (스레드명)
   - `message` (로그 본문)
   - `context` (Key-Value 구조화 메타데이터: 숫자, 불리언, 리스트, 맵)
   - `error` (타입, 메시지, 스택트레이스 라인 배열, 원인 예외 재귀 직렬화)
2. **초고속 RFC 8259 JSON 문자열 이스케이프**:
   - 줄바꿈(`\n`), 탭(`\t`), 따옴표(`\"`), 역슬래시(`\\`), 제어 문자 완벽 변환.
3. **단일 라인 NDJSON / Pretty 출력 모드**:
   - ELK Stack, Fluentd, AWS CloudWatch, Datadog에 최적화된 단일 행 포맷(`compact()`) 및 가독성 디버깅용(`pretty()`).

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.logformat.*;
import java.util.Map;

public class LogFormatExample {
    public static void main(String[] args) {
        StructuredLogFormatter formatter = StructuredLogFormatter.compact();

        // 1. 기본 로그 포맷
        String simpleJson = formatter.format("INFO", "UserService", "User logged in");
        System.out.println(simpleJson);

        // 2. 구조화 컨텍스트가 포함된 로그
        Map<String, Object> ctx = Map.of(
                "userId", 42,
                "ip", "192.168.1.100",
                "role", "ADMIN"
        );
        String ctxJson = formatter.format("WARN", "AuthService", "Unusual login location", ctx);
        System.out.println(ctxJson);

        // 3. 예외 및 스택트레이스 포함 로그
        try {
            throw new IllegalArgumentException("Invalid token format");
        } catch (Exception e) {
            String errJson = formatter.format("ERROR", "TokenValidator", "Validation failed", ctx, e);
            System.out.println(errJson);
        }
    }
}
```
