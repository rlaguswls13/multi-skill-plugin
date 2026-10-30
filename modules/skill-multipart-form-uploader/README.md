# Multipart Form Uploader (`skill-multipart-form-uploader`)

표준 `java.net.http.HttpClient` 및 `HttpRequest`와 함께 사용 가능한 `multipart/form-data` 바디 퍼블리셔(`BodyPublisher`) 플루언트 빌더입니다.

외부 서드파티 라이브러리(Apache Commons FileUpload, Spring Multipart, Unirest 등) 없이 순수 Java 21 표준 라이브러리만으로 텍스트 필드, 바이너리 바이트 배열, `InputStream`, 로컬 파일(`Path`)을 표준 RFC 7578 규격의 멀티파트 업로드 요청으로 손쉽게 조립합니다.

## Features

- **표준 `HttpRequest.BodyPublisher` 호환**: Java 11+ 내장 `HttpClient`와 100% 호환
- **다양한 파트 지원**:
  - 일반 문자열 텍스트 필드 (`addPart(name, value)`)
  - 바이트 배열 바이너리 데이터 (`addPart(name, filename, contentType, byte[])`)
  - 대용량/스트리밍 `InputStream` (`addPart(name, filename, contentType, InputStream)`)
  - 로컬 파일 시스템 경로 (`addPart(name, Path)`)
- **자동 MIME 타입 감지**: `Files.probeContentType`을 통한 로컬 파일 MIME 자동 판별
- **RFC 7578 / RFC 2046 준수**: 안전한 유니크 바운더리 생성 및 특수문자 따옴표 이스케이프 지원
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리만 사용

## Usage

```java
import com.msp.multiupload.MultipartBodyPublisher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

// 1. 멀티파트 폼 데이터 빌더 구성
MultipartBodyPublisher form = MultipartBodyPublisher.newBuilder()
    .addPart("userId", "1004")
    .addPart("memo", "사용자 프로필 업로드")
    .addPart("avatar", Path.of("/path/to/profile.png"));

// 2. 표준 java.net.http.HttpRequest 생성
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/v1/users/upload"))
    .header("Content-Type", form.getContentType()) // multipart/form-data; boundary=...
    .POST(form.build())
    .build();

// 3. 표준 HttpClient로 전송
HttpClient client = HttpClient.newHttpClient();
HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

System.out.println("Status: " + response.statusCode());
System.out.println("Response: " + response.body());
```
