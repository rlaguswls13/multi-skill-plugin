# 📦 `skill-byte-hex-dump-ansi-highlighter`

> **와이어샤크(Wireshark) 및 Hexdump 스타일 3열 바이너리 포맷터 & ANSI 컬러 구문 강조기**

- **아티팩트 좌표**: `com.msp:skill-byte-hex-dump-ansi-highlighter:1.0.0`
- **개별 JAR 산출물**: `modules/skill-byte-hex-dump-ansi-highlighter/build/libs/skill-byte-hex-dump-ansi-highlighter-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **클래식 3열 레이아웃 (Classic 3-Column Wireshark Layout)**:
   - **열 1**: 8자리 16진수 오프셋 (`00000000`)
   - **열 2**: 16개 16진수 바이트 (8바이트씩 두 블록으로 나뉘어 추가 공백 구분)
   - **열 3**: `|...|` 형태의 ASCII 표현 (출력 불가능한 제어문자는 `.` 표시)
2. **시맨틱 ANSI 컬러 하이라이팅**:
   - 널 바이트 (`0x00`) -> 회색 (`GRAY`)
   - 출력 가능 ASCII (`0x20` - `0x7E`) -> 녹색 (`GREEN`)
   - 제어 문자 (`0x01` - `0x1F`, `0x7F`) -> 노란색 (`YELLOW`)
   - 고위 바이너리 바이트 (`0x80` - `0xFF`) -> 자주색 (`MAGENTA`)
   - 컬러 ON/OFF 토글 지원 (`withAnsiColors(boolean)`)
3. **특정 바이트 구간 강조 (Range Highlighting)**:
   - 프로토콜 헤더, 페이로드 등 특정 바이트 오프셋 구간을 원하는 색상으로 강조 (`highlightRange`)
4. **유연한 커스터마이징**:
   - `bytesPerLine` (기본 16)
   - `showOffset`, `showAscii` 토글
   - `uppercaseHex` (대소문자 16진수 선택)

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.hexdump.AnsiColor;
import com.msp.hexdump.HexDumper;
import java.nio.charset.StandardCharsets;

public class HexDumpExample {
    public static void main(String[] args) {
        byte[] payload = "Hello World!\n\0\1\u00ff".getBytes(StandardCharsets.ISO_8859_1);

        // 1. 기본 ANSI 컬러 덤프
        String coloredDump = HexDumper.dump(payload);
        System.out.println(coloredDump);

        // 2. 헤더 구간(0~5 바이트) 빨간색 강조 및 순수 텍스트 덤프
        String customDump = HexDumper.create()
                .withAnsiColors(true)
                .highlightRange(0, 5, AnsiColor.BRIGHT_RED, "Header")
                .withUppercaseHex(true)
                .format(payload);
        System.out.println(customDump);
    }
}
```
