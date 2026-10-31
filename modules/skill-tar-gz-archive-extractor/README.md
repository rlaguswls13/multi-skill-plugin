# 📦 `skill-tar-gz-archive-extractor`

> **순수 Java 21 표준 라이브러리 기반 대용량 `.tar.gz` 및 `.tgz` 아카이브 스트리밍 추출/압축 패키징 및 Zip Slip 방어기**

- **아티팩트 좌표**: `com.msp:skill-tar-gz-archive-extractor:1.0.0`
- **개별 JAR 산출물**: `modules/skill-tar-gz-archive-extractor/build/libs/skill-tar-gz-archive-extractor-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21, `java.util.zip.GZIPInputStream` / `GZIPOutputStream`)

---

## 1. 주요 기능 (Features)

1. **대용량 스트리밍 아카이브 추출 & 생성**:
   - 아카이브 전체를 메모리에 올리지 않고 8KB 스트림 버퍼링으로 OOM 방지
   - 디렉터리 구조 재귀 순회 및 USTAR 512바이트 블록 규격 패킹
2. **Zip Slip 보안 취약점 완벽 차단**:
   - `../../malicious.sh` 등 상위 디렉터리 순회(Path Traversal) 공격을 추출 단계에서 원천 감지하고 `SecurityException` 발생
3. **GNU LongLink (`@LongLink`) 호환**:
   - 100자 이상의 긴 파일명도 표준 GNU TAR 확장 헤더로 안전하게 직렬화 및 역직렬화
4. **목록 사전 조회 (Non-extract Listing)**:
   - 디스크에 파일을 쓰지 않고 스트림만 통과시켜 아카이브 내 메타데이터(`TarEntryInfo`) 조회

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.targz.TarGzExtractor;
import com.msp.targz.TarEntryInfo;

import java.io.*;
import java.nio.file.Path;
import java.util.List;

public class TarGzExample {
    public static void main(String[] args) throws IOException {
        File sourceDir = new File("build/logs");
        File archiveFile = new File("backup.tar.gz");

        // 1. 디렉터리를 .tar.gz 파일로 압축 생성
        try (OutputStream fos = new FileOutputStream(archiveFile)) {
            TarGzExtractor.createArchive(sourceDir, fos);
        }

        // 2. 압축 해제 없이 파일 목록만 조회
        try (InputStream fis = new FileInputStream(archiveFile)) {
            List<TarEntryInfo> entries = TarGzExtractor.listEntries(fis);
            for (TarEntryInfo entry : entries) {
                System.out.printf("[%s] %s (%d bytes)%n",
                        entry.isDirectory() ? "DIR" : "FILE",
                        entry.name(),
                        entry.size());
            }
        }

        // 3. 대상 디렉터리에 안전하게 압축 해제 (Zip Slip 방어)
        File extractTargetDir = new File("build/extracted-logs");
        try (InputStream fis = new FileInputStream(archiveFile)) {
            List<File> extractedFiles = TarGzExtractor.extractToDirectory(fis, extractTargetDir);
            System.out.println("Extracted count: " + extractedFiles.size());
        }
    }
}
```
