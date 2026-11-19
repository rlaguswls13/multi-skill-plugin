# 📦 `skill-process-environment-sanitizer`

> **자식 프로세스(Child Process) 실행 시 API 키, DB 비밀번호, 인증 토큰 유출을 방지하는 환경 변수 살균기**

- **아티팩트 좌표**: `com.msp:skill-process-environment-sanitizer:1.0.0`
- **개별 JAR 산출물**: `modules/skill-process-environment-sanitizer/build/libs/skill-process-environment-sanitizer-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **민감 환경 변수 자동 스크러빙 (Default Regex Blacklist)**:
   - `password`, `secret`, `key`, `token`, `auth`, `credential`, `cert`, `passphrase`, `private` 등 주요 민감 키워드가 포함된 환경 변수를 자식 프로세스 상속에서 원천 차단
2. **화이트리스트(Whitelist) 최우선 보존**:
   - `PATH`, `JAVA_HOME`, `USER`, `LANG`, `TEMP`, `SYSTEMROOT` 등 OS 및 런타임 필수 환경 변수는 블랙리스트와 무관하게 안전 보존
3. **유연한 처리 액션 (REMOVE / MASK / PRESERVE)**:
   - 완전 제거(`REMOVE`): 자식 프로세스 환경 변수 맵에서 키 자체를 삭제
   - 마스킹(`MASK`): 감시/로깅 목적으로 값만 `***REDACTED***`로 대체
4. **`ProcessBuilder` 직접 주입 (`applyTo`)**:
   - `ProcessBuilder.environment()`에 즉시 적용 가능한 래퍼 메서드 제공
5. **감사 추적 보고서 (SanitizationReport)**:
   - 각 환경 변수별 처리 조치(`action`), 판정 사유(`reason`), 스크러빙/마스킹/보존 건수 집계 제공

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.envcleaner.ProcessEnvironmentSanitizer;
import com.msp.envcleaner.SanitizationReport;

import java.util.Map;

public class EnvironmentSanitizerExample {
    public static void main(String[] args) {
        ProcessEnvironmentSanitizer sanitizer = ProcessEnvironmentSanitizer.defaultSanitizer();

        // 1. Map 환경 변수 살균
        Map<String, String> unsafeEnv = Map.of(
            "PATH", "/usr/bin:/bin",
            "DATABASE_PASSWORD", "secret1234",
            "AWS_SECRET_KEY", "AKIAIOSFODNN7EXAMPLE",
            "APP_ENV", "production"
        );

        Map<String, String> cleanEnv = sanitizer.sanitize(unsafeEnv);
        System.out.println("살균된 환경 변수: " + cleanEnv.keySet());
        // [PATH, APP_ENV]

        // 2. ProcessBuilder 연동
        ProcessBuilder pb = new ProcessBuilder("python", "script.py");
        sanitizer.applyTo(pb);

        // 3. 감사 보고서 출력
        SanitizationReport report = sanitizer.audit(unsafeEnv);
        System.out.printf("제거: %d개, 보존: %d개\n", report.scrubbedCount(), report.preservedCount());
    }
}
```
