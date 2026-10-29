# 📦 `skill-temp-file-manager`

> **순수 Java 21 표준 라이브러리 기반 AutoCloseable 임시 파일/디렉터리 생명주기 관리자 (재귀 삭제 및 JVM Shutdown Hook 자동 정리 내장)**

- **아티팩트 좌표**: `com.msp:skill-temp-file-manager:1.0.0`
- **개별 JAR 산출물**: `modules/skill-temp-file-manager/build/libs/skill-temp-file-manager-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **`AutoCloseable` 기반 즉시 삭제**:
   - `try-with-resources` 구문 종료 시 임시 파일 또는 디렉터리가 즉시 삭제됩니다.
2. **디렉터리 재귀적 완전 삭제**:
   - `TempDirectory`는 내부 파일 및 다단계 하위 폴더까지 `Files.walkFileTree`로 남김없이 정리합니다.
3. **수동 삭제 예외 안전성**:
   - 이미 코드가 파일을 먼저 삭제한 경우에도 `close()` 시 예외 없이 부드럽게 무시됩니다.
4. **JVM Shutdown Hook 자동 등록**:
   - 예외로 인해 `close()`가 누락되더라도 JVM 종료 시 등록된 잔여 리소스를 자동 청소합니다.
5. **외부 경로 등록 지원 (`register`)**:
   - 외부에서 이미 생성된 `Path`를 매니저에 등록하여 동일한 생명주기 관리를 적용할 수 있습니다.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.tempfile.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class TempExample {
    public static void main(String[] args) throws Exception {
        // 1. 임시 파일 사용 후 자동 삭제
        try (TempFile tempFile = TempFileManager.createTempFile("data-", ".csv")) {
            Files.writeString(tempFile.path(), "id,name,value\n1,Alice,100");
            System.out.println("Processing file: " + tempFile.path());
        } // 파일 자동 삭제 완료!

        // 2. 임시 작업 디렉터리 및 중첩 파일 생성 후 자동 정리
        try (TempDirectory tempDir = TempFileManager.createTempDirectory("workspace-")) {
            Path file1 = tempDir.createFile("step1.log");
            Path subDir = tempDir.createSubdirectory("cache");
            Path file2 = subDir.resolve("cached.bin");
            Files.writeString(file2, "Binary chunk");

            System.out.println("Temporary workspace: " + tempDir.path());
        } // 내부의 file1, subDir, file2 및 workspace 폴더 전체 자동 삭제 완료!

        // 3. 수동 일괄 정리
        TempFileManager.cleanupAll();
    }
}
```
