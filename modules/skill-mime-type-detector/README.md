# 📦 `skill-mime-type-detector`

> **바이너리 매직 넘버(Magic Bytes) 스니핑 및 파일 확장자 기반 초고속 MIME 타입 감지 라이브러리 (`MimeTypeDetector`)**  
> 외부 라이브러리(Apache Tika 등) 없이 Java 21 표준 라이브러리만으로 100+ 파일 포맷을 밀리초 미만으로 판별

- **아티팩트 좌표**: `com.msp:skill-mime-type-detector:1.0.0`
- **개별 JAR 산출물**: `modules/skill-mime-type-detector/build/libs/skill-mime-type-detector-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리)

---

## 1. 주요 제공 기능 (Features)

1. **바이너리 매직 넘버 스니핑 (Magic Number Sniffing)**:
   - 파일 앞단 32~64바이트 헤더를 검사하여 위조된 확장자나 무작위 파일명이어도 정확한 바이너리 포맷(PNG, JPEG, GIF, WebP, PDF, ZIP, GZIP, MP4, MP3 등) 감지
2. **ZIP 컨테이너 포맷 특화 식별**:
   - `PK\x03\x04` 시그니처를 공유하는 `.docx`, `.xlsx`, `.pptx`, `.jar`, `.apk` 파일 확장자와 결합하여 정확한 OpenXML/Java MIME 반환
3. **100+ 공통 파일 확장자 내장 매핑**:
   - Web(JSON, JS, CSS, HTML), Docs, Audio, Video, Fonts, Code 스크립트 완벽 커버리지
4. **미디어 카테고리 헬퍼**:
   - `isImage()`, `isVideo()`, `isAudio()`, `isText()` 제공

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.mime.MimeTypeDetector;
import java.nio.file.Path;

public class MimeExample {
    public static void main(String[] args) throws Exception {
        // 1. 파일 경로로부터 감지 (헤더 바이트 + 확장자 결합 분석)
        String mimeFromPath = MimeTypeDetector.detect(Path.of("uploads/photo.jpg"));
        System.out.println("MIME: " + mimeFromPath); // image/jpeg

        // 2. 바이너리 헤더 바이트만으로 감지
        byte[] rawHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        String mimeFromBytes = MimeTypeDetector.detect(rawHeader);
        System.out.println("Header MIME: " + mimeFromBytes); // image/png

        // 3. 확장자 기반 감지
        String mimeExt = MimeTypeDetector.detectByExtension("config.json");
        System.out.println("Ext MIME: " + mimeExt); // application/json

        // 4. 타입 카테고리 판별
        if (MimeTypeDetector.isImage(mimeFromPath)) {
            System.out.println("이미지 파일입니다.");
        }
    }
}
```
