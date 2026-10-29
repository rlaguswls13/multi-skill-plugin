# 📦 `skill-path-normalizer`

> **크로스 플랫폼 경로 정규화 및 Path Traversal 공격 방어 가드 라이브러리 (`PathNormalizer`)**  
> `../../` 경로 탈출, URL 인코딩 / 이중 인코딩 공격, 널 바이트 인젝션 차단 및 안전한 파일명 정제(Sanitization)

- **아티팩트 좌표**: `com.msp:skill-path-normalizer:1.0.0`
- **개별 JAR 산출물**: `modules/skill-path-normalizer/build/libs/skill-path-normalizer-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리)

---

## 1. 주요 제공 기능 (Features)

1. **렉시컬 경로 정규화 (`normalize(String)`)**:
   - 디스크 I/O 없이 구분자 통일(`\` -> `/`), 중복 슬래시 축소, `.` 및 `..` 세그먼트를 논리적으로 계산
2. **Path Traversal 보안 가드 (`safeResolve(Path baseDir, String userPath)`)**:
   - 사용자 입력 경로가 기본 디렉터리(`baseDir`) 바깥으로 탈출하려는 모든 시도(`../../`, 절대 경로, 드라이브 루트)를 탐지하고 `SecurityException` 발생
3. **고급 인코딩 공격 및 널 바이트 방어**:
   - URL 인코딩(`%2e%2e`), 이중 인코딩(`%252e%252e`), 널 바이트 인젝션(`\0`, `%00`) 자동 디코딩 및 사전 차단
4. **안전한 파일명 정제 (`sanitizeFileName(String)`)**:
   - OS 예약 문자(`: * ? " < > | \0 / \`) 제거, 앞뒤 마침표/공백 트림, Windows 예약 장치명(`CON`, `PRN`, `AUX`, `NUL`, `COM1~9`, `LPT1~9`) 보호 접두사 부여

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.pathnorm.PathNormalizer;
import java.nio.file.Path;

public class PathExample {
    public static void main(String[] args) {
        Path uploadDir = Path.of("var/uploads");

        // 1. 안전한 하위 경로 해석 (정상)
        Path safeFile = PathNormalizer.safeResolve(uploadDir, "profiles/avatar.png");
        System.out.println("안전한 경로: " + safeFile);

        // 2. Path Traversal 공격 차단
        try {
            PathNormalizer.safeResolve(uploadDir, "../../etc/passwd");
        } catch (SecurityException e) {
            System.err.println("공격 차단됨: " + e.getMessage());
        }

        // 3. 파일명 정제 (업로드 파일명 안전 변환)
        String rawFileName = "??my-report<2026>*.pdf...";
        String cleanFileName = PathNormalizer.sanitizeFileName(rawFileName);
        System.out.println("정제된 파일명: " + cleanFileName); // my-report2026.pdf
    }
}
```
