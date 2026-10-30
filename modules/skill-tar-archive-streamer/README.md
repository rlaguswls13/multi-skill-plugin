# TAR Archive Streamer (`skill-tar-archive-streamer`)

POSIX UStar 표준 규격을 완벽하게 준수하는 초경량 512-바이트 블록 기반 TAR 아카이브 스트리밍 리더 및 라이터입니다. 외부 아파치 코몬즈(commons-compress) 등의 3rd-party 의존성 없이 순수 Java 21 표준 라이브러리로만 구현되었습니다.

## Features
- **POSIX UStar Standard**: 512-바이트 헤더, 8진수(Octal) 메타데이터(크기, 수정시각, 체크섬 등) 자동 계산 및 파싱.
- **스트리밍 아카이브 생성 (`TarWriter`)**: 파일 및 디렉터리 항목을 메모리 버퍼 또는 `OutputStream`에 직접 블록 패딩(512 바이트 정렬)과 1024-바이트 종료 블록으로 작성.
- **스트리밍 아카이브 읽기 (`TarReader`)**: `InputStream`으로부터 TAR 항목들을 순차 디코딩하여 `TarArchiveEntry` 리스트 반환.
- **디렉터리 & 파일 크기 처리**: 디렉터리 경로 자동 슬래시(`/`) 보정 및 가변 길이 페이로드 패딩 완벽 지원.
- **Zero Runtime Dependencies**: 순수 Java 21 표준 I/O 클래스(`java.io.*`, `java.nio.charset.*`).

## Usage

```java
import com.msp.tar.TarWriter;
import com.msp.tar.TarReader;
import com.msp.tar.TarArchiveEntry;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.List;

// 1. TAR 아카이브 생성
TarWriter writer = new TarWriter();
writer.putDirectory("my-app/data");
writer.putEntry("my-app/README.md", "# Hello Pure Java TAR\nThis is pure Java 21.");
writer.putEntry("my-app/data/sample.bin", new byte[]{0x01, 0x02, 0x03});

byte[] tarData = writer.toByteArray();

// 2. TAR 아카이브 디코딩 및 탐색
List<TarArchiveEntry> entries = TarReader.readEntries(tarData);
for (TarArchiveEntry entry : entries) {
    if (entry.isDirectory()) {
        System.out.println("DIR:  " + entry.name());
    } else {
        System.out.println("FILE: " + entry.name() + " (" + entry.size() + " bytes)");
        if (entry.name().endsWith(".md")) {
            System.out.println(entry.contentAsString());
        }
    }
}
```
