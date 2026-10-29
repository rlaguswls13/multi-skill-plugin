# 📦 `skill-fixed-length-parser`

> **외부 라이브러리 없이 금융·통신 전문(Fixed-Length Message)의 바이트 패딩/언패딩, EUC-KR 및 UTF-8 다중 바이트 문자 경계 안전 절삭을 완벽 지원하는 순수 Java 21 단일 모듈**

- **아티팩트 좌표**: `com.msp:skill-fixed-length-parser:1.0.0`
- **개별 JAR 산출물**: `modules/skill-fixed-length-parser/build/libs/skill-fixed-length-parser-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **유연한 전문 스키마 정의 (`FixedLengthSchema`)**:
   - 필드별 바이트 길이, 정렬 방향(`LEFT`, `RIGHT`), 패딩 문자(공백, '0' 등) 설정
   - 문자셋(`UTF-8`, `EUC-KR`, `CP949` 등) 자유 지정
2. **다중 바이트 문자 안전 처리 (Multi-Byte Safe Boundary Truncation)**:
   - 한글 등 멀티바이트 문자가 지정 바이트 길이를 초과할 때 깨진 바이트 조각(Broken byte)을 남기지 않고 온전한 글자 단위로 안전 절삭 후 패딩
3. **타입 안전한 레코드 접근 (`FixedLengthRecord`)**:
   - `getString()`, `getInt()`, `getLong()`, `getDouble()`, 원본 패딩 문자열(`getRaw()`)
4. **상호 변환**:
   - `pack(Map)` ➔ `String` / `byte[]`
   - `unpack(String)` / `unpack(byte[])` ➔ `FixedLengthRecord` / `Map<String, String>`

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.fixedlen.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// 1. 스키마 정의
FixedLengthSchema schema = FixedLengthSchema.builder()
        .charset(StandardCharsets.UTF_8)
        .addField("txId", 10, Alignment.LEFT, ' ')
        .addField("amount", 12, Alignment.RIGHT, '0')
        .addField("currency", 3, Alignment.LEFT, ' ')
        .build();

// 2. 패킹 (Pack)
Map<String, Object> data = Map.of(
        "txId", "TX1001",
        "amount", 54000,
        "currency", "KRW"
);
String packed = schema.pack(data);
System.out.println(packed); // "TX1001    000000054000KRW"

// 3. 언패킹 (Unpack)
FixedLengthRecord record = schema.unpack("TX1001    000000054000KRW");
String txId = record.getString("txId");    // "TX1001"
int amount = record.getInt("amount");      // 54000
String currency = record.getString("currency"); // "KRW"
```
