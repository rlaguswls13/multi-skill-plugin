# HTTP Range Request Handler (`skill-http-range-request-handler`)

RFC 7233 및 RFC 9110 표준을 준수하는 HTTP `Range: bytes=start-end` 요청 헤더 파서 및 `206 Partial Content` 응답 슬라이싱/스트리밍 유틸리티입니다.

대용량 미디어(오디오/비디오 스트리밍), 파일 이어받기, PDF 뷰어 등 부분 콘텐츠 요청을 외부 웹 프레임워크 의존 없이 순수 Java 21만으로 손쉽게 처리할 수 있습니다.

## Features

- **표준 HTTP Range 헤더 파싱**:
  - `bytes=0-499` (구간 지정)
  - `bytes=500-` (특정 오프셋부터 파일 끝까지)
  - `bytes=-500` (파일 끝에서부터 마지막 500바이트 접미사)
  - `bytes=0-50, 100-200` (다중 구간 파싱 지원)
- **범위 만족성 검증 및 HTTP 상태 코드 자동 판별**:
  - 만족 가능 시 `206 Partial Content`
  - 리소스 초과/역전 시 `416 Range Not Satisfiable` 및 `bytes */<totalLength>` 헤더 포맷 제공
- **RFC 표준 범위 보정**:
  - 요청된 `end`가 전체 크기를 초과할 경우 `totalLength - 1`로 자동 보정(Clamping)
- **메모리 절약형 스트리밍 지원**:
  - 바이트 배열 슬라이싱 (`RangeChunkExtractor.extract(byte[], range)`)
  - `InputStream` 슬라이싱 및 Bounded Stream 생성 (`RangeChunkExtractor.extractStream`)
  - `OutputStream` 직접 복사 파이프라인 (`RangeChunkExtractor.copy`)

## Usage

```java
import com.msp.httprange.ByteRange;
import com.msp.httprange.RangeHeaderParser;
import com.msp.httprange.RangeChunkExtractor;

import java.io.InputStream;
import java.io.OutputStream;

long fileSize = 10_000_000L; // 10 MB 파일
String rangeHeader = "bytes=1000-4999"; // 클라이언트 요청 헤더

// 1. Range 헤더 파싱
ByteRange range = RangeHeaderParser.parse(rangeHeader, fileSize);

if (!range.isSatisfiable()) {
    // 416 Range Not Satisfiable 응답
    // response.setStatus(range.httpStatus()); // 416
    // response.setHeader("Content-Range", range.toContentRangeHeader()); // "bytes */10000000"
    return;
}

// 2. 206 Partial Content 응답 헤더 설정
// response.setStatus(range.httpStatus()); // 206
// response.setHeader("Content-Range", range.toContentRangeHeader()); // "bytes 1000-4999/10000000"
// response.setHeader("Content-Length", String.valueOf(range.length())); // 4000

// 3. 파일 스트림에서 해당 청크만 클라이언트 출력 스트림으로 복사
try (InputStream fileIn = openFileInputStream()) {
    RangeChunkExtractor.copy(fileIn, responseOutputStream, range);
}
```
