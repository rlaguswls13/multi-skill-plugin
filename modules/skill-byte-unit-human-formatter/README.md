# 📦 `skill-byte-unit-human-formatter`

> **IEC (1024진 바이너리: KiB, MiB...) 및 SI (1000진 데시멀: KB, MB...) 바이트 크기 포매터 및 파서**

- **아티팩트 좌표**: `com.msp:skill-byte-unit-human-formatter:1.0.0`
- **개별 JAR 산출물**: `modules/skill-byte-unit-human-formatter/build/libs/skill-byte-unit-human-formatter-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **IEC 및 SI 표준 바이트 단위 지원**:
   - `IEC`: 1024진법 (`B`, `KiB`, `MiB`, `GiB`, `TiB`, `PiB`, `EiB`)
   - `SI`: 1000진법 (`B`, `KB`, `MB`, `GB`, `TB`, `PB`, `EB`)
2. **유연한 포매팅**:
   - 자리수(소수점 자릿수) 지정 포매팅 (`format(bytes, standard, decimalPlaces)`).
   - 기본 편의 메서드 `format(bytes)` (IEC 기준, 소수점 2자리).
   - 음수 바이트 및 `Long.MAX_VALUE` / `Long.MIN_VALUE` 경계값 안전 처리 (오버플로우 방지).
3. **대소문자 무관 및 공백 관용 파서**:
   - `"1.5 GiB"`, `"500MB"`, `"1024 B"`, `"2.3KiB"`, `"-10 MiB"` 등 파싱 지원.
   - `BigDecimal` 기반 정밀 계산으로 소수점 오차 방지 및 64비트 정수 오버플로우 감지.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.byteunit.ByteUnitFormatter;
import com.msp.byteunit.ByteUnitStandard;

public class ByteUnitExample {
    public static void main(String[] args) {
        long memoryBytes = 1572864L; // 1.5 MiB

        // 1. 기본 IEC 포매팅 (1.50 MiB)
        String humanReadable = ByteUnitFormatter.format(memoryBytes);
        System.out.println("IEC 2-decimal: " + humanReadable);

        // 2. SI 1000진법 포매팅 (1.57 MB)
        String siReadable = ByteUnitFormatter.format(memoryBytes, ByteUnitStandard.SI, 2);
        System.out.println("SI format: " + siReadable);

        // 3. 문자열 파싱 (양방향 변환)
        long parsedBytes = ByteUnitFormatter.parse("1.5 GiB");
        System.out.println("Parsed bytes: " + parsedBytes); // 1610612736
    }
}
```
