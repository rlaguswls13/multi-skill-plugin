# 📦 `skill-file-watcher`

> **Java NIO `WatchService` 기반 재귀적 디렉터리 모니터링 및 이벤트 디바운싱(Debouncing) 라이브러리 (`FileWatcher`, `FileWatchers`)**

- **아티팩트 좌표**: `com.msp:skill-file-watcher:1.0.0`
- **개별 JAR 산출물**: `modules/skill-file-watcher/build/libs/skill-file-watcher-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리)

---

## 1. 주요 제공 기능 (Features)

1. **OS 파일시스템 이벤트 디바운싱 (Debouncing)**: 파일 저장 시 발생하는 연속적인 중복 `ENTRY_MODIFY` 이벤트를 조용한 시간(기본 300ms) 동안 수렴하여 단 1회의 콜백으로 정리
2. **동적 재귀 감시 (Recursive Monitoring)**: 최상위 디렉터리뿐만 아니라 하위 폴더 전체를 감시하며, 런타임에 새로 생성되는 하위 폴더도 자동으로 감시 대상에 등록
3. **이벤트 병합 (Event Coalescing)**: 생성 후 즉시 수정(`CREATED` + `MODIFIED` -> `CREATED`), 생성 후 즉시 삭제되는 임시 파일 자동 필터링
4. **간결한 수명주기 관리**: `start()`, `stop()`, `isRunning()`, `AutoCloseable` 구현

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.filewatch.*;
import java.nio.file.Path;
import java.time.Duration;

public class WatcherExample {
    public static void main(String[] args) throws Exception {
        Path watchDir = Path.of("configs");

        // 1. 하위 폴더 재귀 감시 + 500ms 디바운싱
        try (FileWatcher watcher = FileWatchers.watchRecursive(watchDir, Duration.ofMillis(500), (path, type) -> {
            System.out.printf("[파일 변경 감지] %s -> %s%n", type, path.getFileName());
            if (type == FileChangeType.MODIFIED) {
                reloadConfiguration(path);
            }
        })) {
            System.out.println("디렉터리 감시 중... (종료하려면 엔터 입력)");
            System.in.read();
        }
    }

    private static void reloadConfiguration(Path path) {
        System.out.println("설정 파일 리로드: " + path);
    }
}
```
