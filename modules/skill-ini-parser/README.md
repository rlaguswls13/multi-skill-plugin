# 📦 `skill-ini-parser`

> **순수 Java 21 표준 라이브러리 기반의 Windows & Linux INI 설정 파일 섹션 및 키-값 파서/직렬화 유틸리티**

- **아티팩트 좌표**: `com.msp:skill-ini-parser:1.0.0`
- **개별 JAR 산출물**: `modules/skill-ini-parser/build/libs/skill-ini-parser-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **`IniParser.parse(text / path / reader)`**:
   - `[SectionName]` 섹션 헤더 및 글로벌 프로퍼티(섹션 헤더 이전의 키-값) 파싱.
   - 키-값 구분자 `=` 및 `:` 완벽 지원.
   - 주석 처리: `;` 및 `#` 라인 주석 및 인라인 주석 지원 (따옴표 내부 주석 보호).
   - 큰따옴표(`"..."`) 및 작은따옴표(`'...'`) 값 언쿼팅(Unquoting).
2. **`IniFile`**:
   - `get(section, key)`, `get(key)` (글로벌 키 조회).
   - 타입 변환 및 기본값 폴백: `getInt(...)`, `getLong(...)`, `getDouble(...)`, `getBoolean(...)`.
   - `hasSection(section)`, `getSections()`, `getSection(section)`.
   - `set(section, key, value)` 및 `set(key, value)`.
3. **`IniWriter.write(ini)` / `ini.toIni()`**:
   - 글로벌 프로퍼티 및 섹션별 키-값을 표준 INI 포맷 문자열로 직렬화.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.ini.IniFile;
import com.msp.ini.IniParser;
import com.msp.ini.IniWriter;

public class IniExample {
    public static void main(String[] args) {
        String iniContent = """
            ; Global settings
            appName = MyBackend
            env = staging

            [database]
            host = db.internal.local
            port = 5432
            pool_size = 20
            ssl = true ; enable TLS

            [logging]
            level = INFO
            file = "logs/app.log"
            """;

        // 1. 파싱
        IniFile ini = IniParser.parse(iniContent);
        System.out.println("App: " + ini.get("appName"));
        System.out.println("DB Host: " + ini.get("database", "host"));
        System.out.println("DB Port: " + ini.getInt("database", "port", 3306));
        System.out.println("SSL: " + ini.getBoolean("database", "ssl", false));

        // 2. 값 수정 및 직렬화
        ini.set("database", "port", 5433);
        String updated = IniWriter.write(ini);
        System.out.println(updated);
    }
}
```
