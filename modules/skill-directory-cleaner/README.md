# 📦 `skill-directory-cleaner`

> **시간 기준(Time-based) 및 파일 패턴(Glob Pattern) 기반 디렉터리 정리 라이브러리 (`DirectoryCleaner`, `CleanOptions`, `CleanReport`)**  
> 파일 수정 시각, 파일 패턴 필터링, 안전한 빈 폴더 삭제, 실제 삭제 없는 시뮬레이션(Dry-run) 기능 제공

- **아티팩트 좌표**: `com.msp:skill-directory-cleaner:1.0.0`
- **개별 JAR 산출물**: `modules/skill-directory-cleaner/build/libs/skill-directory-cleaner-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리)

---

## 1. 주요 제공 기능 (Features)

1. **시간 기반 파일 정리 (`olderThan(Duration)`)**:
   - `lastModifiedTime`을 기준으로 지정한 시간(예: 7일 전, 24시간 전)보다 오래된 파일만 정확하게 선별 삭제
2. **Glob 파일 패턴 매칭 (`globPattern("*.log")`)**:
   - `*.log`, `*.tmp`, `dump-*.csv` 등 유연한 와일드카드 패턴 지원
3. **빈 폴더 자동 정리 (`deleteEmptyDirs(true)`)**:
   - 하위 파일이 삭제되어 빈 폴더가 되거나 기존에 비어있던 디렉터리를 Bottom-Up 방식으로 안전하게 제거 (루트 디렉터리는 보호)
4. **시뮬레이션 모드 (`dryRun(true)`)**:
   - 디스크 실제 삭제 없이 삭제 대상 파일 수, 용량(Freed Bytes), 대상 폴더 목록을 사전 검증
5. **완벽한 결과 리포트 (`CleanReport`)**:
   - 삭제 파일 개수, 삭제 디렉터리 개수, 확보된 바이트 용량, 실패 경로 목록 제공

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.dirclean.CleanOptions;
import com.msp.dirclean.CleanReport;
import com.msp.dirclean.DirectoryCleaner;
import java.nio.file.Path;
import java.time.Duration;

public class CleanerExample {
    public static void main(String[] args) throws Exception {
        Path logDir = Path.of("var/logs");

        // 1. 30일 이상 지난 *.log 파일 삭제 및 빈 폴더 정리 (Dry Run 시뮬레이션)
        CleanOptions dryRunOptions = CleanOptions.builder()
                .olderThan(Duration.ofDays(30))
                .globPattern("*.log")
                .deleteEmptyDirs(true)
                .dryRun(true)
                .build();

        CleanReport preview = DirectoryCleaner.clean(logDir, dryRunOptions);
        System.out.printf("[Dry Run] 삭제 대상 파일: %d개, 예상 확보 용량: %d bytes%n",
                preview.deletedFileCount(), preview.freedBytes());

        // 2. 실제 디스크 삭제 실행
        CleanOptions realOptions = CleanOptions.builder()
                .olderThan(Duration.ofDays(30))
                .globPattern("*.log")
                .deleteEmptyDirs(true)
                .dryRun(false)
                .build();

        CleanReport result = DirectoryCleaner.clean(logDir, realOptions);
        System.out.printf("[삭제 완료] 삭제된 파일: %d개, 삭제된 폴더: %d개%n",
                result.deletedFileCount(), result.deletedDirCount());
    }
}
```
