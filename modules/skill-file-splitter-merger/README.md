# 📦 `skill-file-splitter-merger`

> **순수 Java 21 표준 라이브러리 기반 대용량 파일 청크 분할기(바이트 크기/라인 수 기준) 및 바이트 레벨 무결성 병합기**

- **아티팩트 좌표**: `com.msp:skill-file-splitter-merger:1.0.0`
- **개별 JAR 산출물**: `modules/skill-file-splitter-merger/build/libs/skill-file-splitter-merger-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **바이트 크기 기준 청크 분할 (`FileSplitter.splitBySize`)**:
   - 64KB 스트리밍 버퍼를 활용하여 임의 크기의 대형 파일을 지정된 바이트 크기 단위 청크(`.part0001`, `.part0002`...)로 정확히 분할합니다.
2. **텍스트 라인 기준 분할 (`FileSplitter.splitByLines`)**:
   - 텍스트/로그 파일을 청크당 최대 라인 수 기준으로 분할합니다.
3. **바이트 레벨 순차 병합 (`FileMerger.merge`)**:
   - 분할된 청크 목록을 순서대로 읽어 원본 파일 형태로 스트리밍 병합합니다.
4. **SHA-256 무결성 자동 검증 병합**:
   - `FileMerger.merge(chunks, dest, expectedSha256)` 호출 시 병합 완료 직후 스트리밍 해시를 검증하며, 불일치 시 `IllegalStateException`을 발생시켜 데이터 손상을 방지합니다.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.filesplit.FileSplitter;
import com.msp.filesplit.FileMerger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class SplitMergeExample {
    public static void main(String[] args) throws Exception {
        Path largeFile = Paths.get("data/backup.tar");
        Path chunksDir = Paths.get("data/chunks");
        Path restoredFile = Paths.get("data/restored.tar");

        // 1. 원본 파일 SHA-256 체크섬 사전 계산
        String originalChecksum = FileMerger.computeSha256(largeFile);

        // 2. 50MB 단위로 청크 분할
        long chunkSize = 50L * 1024 * 1024;
        List<Path> chunks = FileSplitter.splitBySize(largeFile, chunkSize, chunksDir);
        System.out.println("Created " + chunks.size() + " chunk parts.");

        // 3. 분할 청크 병합 및 무결성 검증
        FileMerger.merge(chunks, restoredFile, originalChecksum);
        System.out.println("File successfully restored and integrity verified!");

        // 4. 텍스트 파일 라인 단위 분할 예시 (1,000줄씩)
        List<Path> logChunks = FileSplitter.splitByLines(Paths.get("data/app.log"), 1000, Paths.get("data/log_chunks"));
    }
}
```
