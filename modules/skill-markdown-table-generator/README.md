# 📊 skill-markdown-table-generator

객체 리스트/Map 컬렉션으로부터 정렬된 GitHub Flavored Markdown (GFM) 표(Table)를 생성하는 경량 포맷터 라이브러리입니다.

## ✨ 주요 기능
- **열별 3단계 정렬 지원**: `LEFT` (`:---`), `CENTER` (`:---:`), `RIGHT` (`---:`)
- **자동 너비 패딩**: 컬럼 헤더, 본문 텍스트, GFM 구분자 최소 길이를 동적으로 계산하여 미려한 공백 패딩 적용
- **Map/Object 자동 바인딩**: `fromMaps()` 팩토리를 통한 `List<Map<String, ?>>` 일괄 테이블 변환
- **특수문자 자동 이스케이프**: 마크다운 파이프 기호(`|` -> `\|`) 및 개행(`\n` -> `<br>`) 안전 치환
- **Pure Java 21**: 외부 런타임 3rd-party 의존성 Zero

---

## 🚀 빠른 시작 (Quick Start)

### 1. 빌더 패턴으로 표 작성
```java
import com.msp.mdtable.MarkdownTableBuilder;
import com.msp.mdtable.TableAlignment;

public class Example {
    public static void main(String[] args) {
        MarkdownTableBuilder table = new MarkdownTableBuilder()
                .addColumn("ID", TableAlignment.RIGHT)
                .addColumn("Name", TableAlignment.LEFT)
                .addColumn("Status", TableAlignment.CENTER)
                .addRow(1, "Alice", "ACTIVE")
                .addRow(2, "Bob", "PENDING")
                .addRow(3, "Charlie", "DONE");

        System.out.println(table.toMarkdown());
    }
}
```

**출력 결과:**
```markdown
| ID | Name    | Status  |
|---:| :------ | :-----: |
|  1 | Alice   | ACTIVE  |
|  2 | Bob     | PENDING |
|  3 | Charlie |  DONE   |
```

### 2. Map 컬렉션에서 일괄 변환
```java
List<Map<String, Object>> rows = List.of(
    Map.of("Metric", "CPU Usage", "Value", "42%"),
    Map.of("Metric", "Memory", "Value", "1.2 GB")
);

String markdown = MarkdownTableBuilder.fromMaps(
    rows,
    List.of("Metric", "Value"),
    Map.of("Value", TableAlignment.RIGHT)
).toMarkdown();
```
