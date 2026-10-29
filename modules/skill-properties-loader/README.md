# 📦 `skill-properties-loader`

> **순수 Java 21 표준 라이브러리 기반의 계층적 `.env` 및 `.properties` 설정 병합기 및 환경 변수 확장 라이브러리**

- **아티팩트 좌표**: `com.msp:skill-properties-loader:1.0.0`
- **개별 JAR 산출물**: `modules/skill-properties-loader/build/libs/skill-properties-loader-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **다중 소스 병합 및 우선순위 (Merging & Precedence)**:
   - `.properties` 및 `.env` 파일을 원하는 순서대로 체이닝 로드.
   - 나중에 로드된 파일 또는 직접 설정한 `set(k, v)` 값이 이전 설정을 우선하여 덮어씀.
2. **플레이스홀더 및 환경 변수 치환 (`${VAR:-default}`)**:
   - 로드된 프로퍼티 간 상호 참조 (`${HOST}:${PORT}`).
   - 기본값 폴백 문법 지원: `${VAR:-default}` 또는 `${VAR:default}`.
   - OS 환경 변수(`System.getenv`) 및 Java 시스템 프로퍼티(`System.getProperty`) 확장 연동.
3. **타입 안전 게터**:
   - `getString(key, default)`
   - `getInt(key, default)`, `getLong(key, default)`, `getDouble(key, default)`
   - `getBoolean(key, default)`: `true`, `1`, `yes`, `on` / `false`, `0`, `no`, `off` 자동 변환.
4. **`.env` 완전 지원**:
   - `export KEY=VALUE`, 큰따옴표/작은따옴표, 줄바꿈 이스케이프(`\n`), 인라인 주석(`#`) 완벽 처리.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.props.PropertiesLoader;
import java.nio.file.Path;

public class PropsExample {
    public static void main(String[] args) {
        PropertiesLoader config = PropertiesLoader.builder()
                .loadProperties(Path.of("config/application.properties"))
                .loadDotEnv(Path.of(".env.local"))
                .enableEnvExpansion()
                .build();

        String dbUrl = config.getString("database.url");
        int port = config.getInt("server.port", 8080);
        boolean debug = config.getBoolean("app.debug", false);

        System.out.println("DB URL: " + dbUrl);
        System.out.println("Port: " + port);
        System.out.println("Debug: " + debug);
    }
}
```
