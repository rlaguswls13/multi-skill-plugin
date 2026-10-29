# 📦 `skill-csv-tsv-streamer`

> **메모리 부족(OOM) 방지 행 단위 스트리밍 CSV/TSV 리더 및 라이터 (`CsvStreamReader`, `CsvStreamWriter`, `CsvStreamer`)**

- **아티팩트 좌표**: `com.msp:skill-csv-tsv-streamer:1.0.0`
- **개별 JAR 산출물**: `modules/skill-csv-tsv-streamer/build/libs/skill-csv-tsv-streamer-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리)

---

## 1. 주요 제공 기능 (Features)

1. **메모리 절약 스트리밍 (OOM-Safe Streaming)**: 파일 전체를 힙 메모리에 적재하지 않고 한 줄씩 읽는 `Stream<CsvRow>` 및 `Iterator<CsvRow>` 제공
2. **완전한 RFC-4180 준수**: 따옴표로 감싸진 필드(`"a,b"`), 이스케이프된 따옴표(`""`), 개행 문자가 포함된 필드(`"line1\nline2"`) 완벽 처리
3. **구분자 형식 전환**: CSV(콤마), TSV(탭), 세미콜론, 파이프(`|`) 등 임의의 커스텀 구분자 지원
4. **타입 안전한 CsvRow 접근**: `getAsInt()`, `getAsLong()`, `getAsDouble()`, `getAsBoolean()`, `getOptional()`, `toMap()`
5. **자동 이스케이프 라이터**: 필요 시에만 자동으로 따옴표 및 이스케이프를 적용하는 고성능 스트리밍 라이터

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.csv.*;
import java.nio.file.Path;

public class CsvExample {
    public static void main(String[] args) throws Exception {
        Path input = Path.of("transactions.csv");

        // 1. 대용량 파일 스트리밍 처리 (메모리 절약)
        try (CsvStreamReader reader = CsvStreamer.read(input)) {
            reader.stream()
                .filter(row -> row.getAsInt("amount") > 50000)
                .forEach(row -> {
                    System.out.printf("고액 결제 [%s]: %s (%d원)%n",
                        row.get("tx_id"), row.get("user"), row.getAsInt("amount"));
                });
        }

        // 2. CSV 파일 쓰기
        Path output = Path.of("output.csv");
        try (CsvStreamWriter writer = CsvStreamer.write(output)) {
            writer.writeHeader("id", "name", "note");
            writer.writeRow(1, "Alice", "정상 회원");
            writer.writeRow(2, "Bob", "쉼표, 및 \"따옴표\" 포함");
        }
    }
}
```
