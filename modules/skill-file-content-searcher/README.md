# 📦 `skill-file-content-searcher`

> **재귀적 파일 내용 검색(Grep) 및 정규식 탐색 라이브러리 (`FileSearcher`, `SearchQuery`, `SearchResult`)**  
> 대용량 파일 메모리 세이프 스트리밍, 바이너리 파일 자동 감지 스킵, 행 번호 및 매치 오프셋 제공

- **아티팩트 좌표**: `com.msp:skill-file-content-searcher:1.0.0`
- **개별 JAR 산출물**: `modules/skill-file-content-searcher/build/libs/skill-file-content-searcher-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리)

---

## 1. 주요 제공 기능 (Features)

1. **리터럴 문자열 및 정규표현식(Regex) 검색**:
   - `pattern("...")`, `isRegex(true/false)`, `caseSensitive(true/false)` 완벽 지원
2. **메모리 세이프 스트리밍 (Line-by-Line Streaming)**:
   - 파일 전체를 메모리에 올리지 않고 `BufferedReader` 기반 한 줄씩 스트리밍 처리하여 대용량 파일도 OOM 없이 안정적 탐색
3. **바이너리 파일 자동 스킵 (`skipBinaryFiles(true)`)**:
   - 파일 선두 바이트의 `0x00` 널 바이트를 감지하여 실행 파일, 이미지, 클래스 파일 등 비텍스트 파일 자동 제외
4. **정확한 위치 메타데이터 (`SearchResult`)**:
   - 파일 경로(`path`), 1 기반 행 번호(`lineNumber`), 전체 줄 내용(`lineContent`), 매치 시작/끝 인덱스(`matchStart`, `matchEnd`), 매칭 텍스트(`matchedText()`) 제공
5. **Glob 파일 필터링 및 최대 매칭 수 제한**:
   - `fileGlob("*.java")`, `maxResults(100)`, `maxDepth(5)` 제어

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.filesearch.FileSearcher;
import com.msp.filesearch.SearchQuery;
import com.msp.filesearch.SearchResult;
import java.nio.file.Path;
import java.util.List;

public class SearchExample {
    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of("src");

        // 1. 단순 문자열 검색 (특정 확장자 필터)
        List<SearchResult> results = FileSearcher.search(projectRoot, "TODO", "*.java");
        for (SearchResult res : results) {
            System.out.printf("[%s:%d] %s%n",
                    res.path().getFileName(), res.lineNumber(), res.lineContent().trim());
        }

        // 2. 정규식 패턴 검색 (대소문자 무시 및 결과 개수 제한)
        SearchQuery query = SearchQuery.builder()
                .pattern("api_key\\s*=\\s*['\"][a-zA-Z0-9]+['\"]")
                .isRegex(true)
                .caseSensitive(false)
                .fileGlob("*.properties")
                .maxResults(10)
                .build();

        List<SearchResult> apiKeys = FileSearcher.search(projectRoot, query);
        System.out.println("발견된 API 키 개수: " + apiKeys.size());
    }
}
```
