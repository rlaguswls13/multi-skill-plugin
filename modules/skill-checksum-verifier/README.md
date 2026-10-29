# 📦 `skill-checksum-verifier`

> **순수 Java 21 표준 라이브러리 기반 대용량 파일 스트리밍 SHA-256, MD5, CRC32 체크섬 생성 및 무결성 검증기**

- **아티팩트 좌표**: `com.msp:skill-checksum-verifier:1.0.0`
- **개별 JAR 산출물**: `modules/skill-checksum-verifier/build/libs/skill-checksum-verifier-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **스트리밍 기반 O(1) 메모리 처리**:
   - 64KB 고정 버퍼 스트리밍 방식을 사용하여 수십 GB 이상의 초대형 파일도 힙 메모리 부하 없이 체크섬을 계산합니다.
2. **지원 알고리즘**:
   - **SHA-256**: 64자리 16진수 (`HexFormat` 사용)
   - **MD5**: 32자리 16진수
   - **CRC-32**: 8자리 16진수 및 64비트 정수형(`long`)
3. **무결성 검증 (`verify*`)**:
   - 대소문자 무관(case-insensitive) 및 앞뒤 공백 자동 처리.
   - 변조 발생 시 즉각 불리언 `false` 반환.
4. **유연한 입력 소스**:
   - `java.nio.file.Path`, `byte[]`, `String` 지원.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.checksum.ChecksumVerifier;
import com.msp.checksum.ChecksumAlgorithm;
import java.nio.file.Path;

public class ChecksumExample {
    public static void main(String[] args) throws Exception {
        Path file = Path.of("large-dataset.iso");

        // 1. 체크섬 계산
        String sha256 = ChecksumVerifier.sha256(file);
        String md5 = ChecksumVerifier.md5(file);
        String crc32 = ChecksumVerifier.crc32(file);

        System.out.println("SHA-256: " + sha256);
        System.out.println("MD5:     " + md5);
        System.out.println("CRC-32:  " + crc32);

        // 2. 무결성 검증
        boolean matches = ChecksumVerifier.verifySha256(
                file,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );
        System.out.println("Integrity intact: " + matches);

        // 3. Enum 통합 인터페이스
        String calculated = ChecksumAlgorithm.SHA_256.compute(file);
        boolean valid = ChecksumAlgorithm.SHA_256.verify(file, calculated);
    }
}
```
