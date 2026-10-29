# 📦 `skill-ascii-table-printer`

> **외부 라이브러리 없이 순수 Java 21 표준 라이브러리로 CLI 터미널, 애플리케이션 로그, GitHub Markdown 문서를 위한 테이블 포맷팅(ASCII, Unicode Box, Markdown, Minimal), 열 정렬(좌/중/우), 동아시아 전각 문자(한글/CJK) 폭 계산 및 다중 행(Multi-line) 셀을 지원하는 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-ascii-table-printer:1.0.0`
- **개별 JAR 산출물**: `modules/skill-ascii-table-printer/build/libs/skill-ascii-table-printer-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **다양한 테이블 렌더링 스타일 (`TableStyle`)**:
   - `ASCII`: 전통적인 7비트 ASCII (`+---+`, `| |`) 표준 테이블.
   - `UNICODE_BOX`: 미려한 유니코드 박스 드로잉(`┌─┬─┐`, `│ │ │`, `├─┼─┤`, `└─┴─┘`) 테이블.
   - `MARKDOWN`: GitHub Flavored Markdown과 100% 호환되는 마크다운 테이블 문법(`| :--- | :---: | ---: |`).
   - `MINIMAL`: 로그 파일 및 CLI 콘솔 출력에 최적화된 무경계 심플 테이블.
2. **동아시아 전각 문자(East Asian Width) 너비 보정**:
   - 한글/한자/일본어는 고정폭 터미널에서 영문 2칸을 차지합니다.
   - 본 모듈은 문자열 길이(`length()`)가 아닌 **시각적 표시 너비(Display Width)**를 정밀 계산하여 한글이 포함된 표도 틀어짐 없이 완벽하게 정렬합니다.
3. **다중 행 셀(Multi-line Cell) 지원**:
   - 셀 내부 개행 문자(`\n`)를 자동 감지하여 동일 행의 다른 컬럼과 높이를 맞춰 줄바꿈 렌더링.
4. **유연한 플루언트 빌더 (`AsciiTable.builder()`)**:
   - 컬럼별 정렬(LEFT, CENTER, RIGHT), 패딩 조절, `null` 대체 문자 지정 지원.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.asciitable.*;

public class Example {
    public static void main(String[] args) {
        // 1. 유니코드 박스 스타일 테이블
        String boxTable = AsciiTable.builder()
                .style(TableStyle.UNICODE_BOX)
                .header("ID", "이름", "직급", "상태")
                .alignment(0, ColumnAlignment.RIGHT)
                .alignment(1, ColumnAlignment.LEFT)
                .alignment(2, ColumnAlignment.CENTER)
                .row("1", "홍길동", "팀장", "재직")
                .row("2", "김철수", "수석", "출장")
                .row("3", "Jane Doe", "Director", "휴가")
                .render();

        System.out.println(boxTable);

        // 2. GitHub 마크다운 테이블 생성
        String mdTable = AsciiTable.builder()
                .style(TableStyle.MARKDOWN)
                .header("Metric", "Value", "Unit")
                .alignment(0, ColumnAlignment.LEFT)
                .alignment(1, ColumnAlignment.RIGHT)
                .alignment(2, ColumnAlignment.CENTER)
                .row("Latency p99", "1.45", "ms")
                .row("Throughput", "12,500", "ops/s")
                .render();

        System.out.println(mdTable);

        // 3. 셀 내 줄바꿈(Multi-line) 포함 테이블
        String multiLineTable = AsciiTable.builder()
                .style(TableStyle.ASCII)
                .header("Task", "Description")
                .row("Deploy", "Step 1: Build JAR\nStep 2: Restart Service")
                .row("Verify", "Check health endpoint")
                .render();

        System.out.println(multiLineTable);
    }
}
```
