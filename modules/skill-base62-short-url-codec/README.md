# 📦 `skill-base62-short-url-codec`

> **순수 Java 21 표준 라이브러리 기반 표준 Base62 (0-9, a-z, A-Z) 양방향 인코더/디코더**

- **아티팩트 좌표**: `com.msp:skill-base62-short-url-codec:1.0.0`
- **개별 JAR 산출물**: `modules/skill-base62-short-url-codec/build/libs/skill-base62-short-url-codec-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **표준 Base62 알파벳 (`0-9`, `a-z`, `A-Z`)**:
   - URL Shortener(단축 URL), Nano ID, 분산 ID 압축에 최적화된 URL-Safe 인코딩
2. **다양한 데이터 타입 양방향 변환**:
   - `long`: 원시 정수형 초고속 인코딩 & 오버플로우 감지 디코딩
   - `BigInteger`: 임의 정밀도 대형 정수 완벽 지원
   - `byte[]`: 선행 `0x00` 바이트를 완벽 보존하는 바이너리 코덱
   - `UUID`: 128비트 UUID를 **고정 길이 22자리 문자열**로 무손실 압축 변환
3. **엄격한 입력 검증**:
   - 허용되지 않은 문자나 길이 초과에 대해 명확한 예외 발생

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.base62.Base62Codec;
import java.util.UUID;

public class Base62Example {
    public static void main(String[] args) {
        // 1. 단축 URL ID 인코딩
        long databaseId = 125301243L;
        String shortCode = Base62Codec.encode(databaseId);
        System.out.println("Short URL Code: " + shortCode); // e.g., 8yL6x
        System.out.println("Decoded ID: " + Base62Codec.decodeToLong(shortCode));

        // 2. 128비트 UUID 22자리 압축
        UUID originalUuid = UUID.randomUUID();
        String compactUuid = Base62Codec.encodeUuid(originalUuid);
        System.out.println("22-char UUID: " + compactUuid); // e.g., 3kX9bW1z... (length 22)

        UUID restoredUuid = Base62Codec.decodeUuid(compactUuid);
        System.out.println("UUID Restored Match: " + originalUuid.equals(restoredUuid));
    }
}
```
