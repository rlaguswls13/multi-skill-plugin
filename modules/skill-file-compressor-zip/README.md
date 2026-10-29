# 📦 `skill-file-compressor-zip`

> **순수 Java 21 표준 라이브러리 기반 파일/디렉터리 ZIP 압축 및 해제 유틸리티 (ZipSlip 경로 조작 및 ZipBomb 비율 공격 방어 내장)**

- **아티팩트 좌표**: `com.msp:skill-file-compressor-zip:1.0.0`
- **개별 JAR 산출물**: `modules/skill-file-compressor-zip/build/libs/skill-file-compressor-zip-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **단일 파일 압축 (`compressFile`)**:
   - 개별 파일을 표준 ZIP 포맷으로 압축합니다.
2. **다중 레벨 디렉터리 압축 (`compressDirectory`)**:
   - `Files.walkFileTree` 기반으로 빈 폴더를 포함하여 중첩 디렉터리를 재귀적으로 압축합니다.
   - `includeRoot` 옵션 지원: 루트 폴더명 포함 여부 제어.
   - 대상 ZIP 파일이 압축 대상 디렉터리 내부에 위치할 경우 무한 압축 루프 자동 방지.
3. **안전한 압축 해제 (`decompress`)**:
   - **ZipSlip 방어**: `../` 등 경로 조작을 통해 타깃 디렉터리 외부로 파일이 탈출하는 행위를 탐지하고 즉시 `SecurityException` 발생.
   - **ZipBomb 방어**: 과도한 압축 해제 비율(초과 배율) 및 총 해제 용량 한도(바이트 단위), 파일 엔트리 개수 상한을 검증하여 서비스 거부(DoS) 공격 방어.
   - `overwrite` 플래그로 기존 파일 덮어쓰기 제어 (`FileAlreadyExistsException`).

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.zip.ZipCompressor;
import com.msp.zip.ZipGuardOptions;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ZipExample {
    public static void main(String[] args) throws Exception {
        Path sourceDir = Paths.get("data/logs");
        Path archiveFile = Paths.get("backup/logs-2026.zip");
        Path extractDir = Paths.get("data/extracted");

        // 1. 디렉터리 압축 (루트 폴더 포함)
        ZipCompressor.compressDirectory(sourceDir, archiveFile, true);

        // 2. 단일 파일 압축
        ZipCompressor.compressFile(Paths.get("data/report.pdf"), Paths.get("data/report.zip"));

        // 3. 안전한 압축 해제 (ZipSlip & ZipBomb 기본 방어)
        ZipCompressor.decompress(archiveFile, extractDir, true);

        // 4. 커스텀 보안 정책 적용 해제
        ZipGuardOptions customGuard = new ZipGuardOptions(
                50,                     // 최대 압축 해제 비율 50배
                500L * 1024 * 1024,     // 최대 해제 총 용량 500MB
                10_000                  // 최대 파일 엔트리 수
        );
        ZipCompressor.decompress(archiveFile, extractDir, true, customGuard);
    }
}
```
