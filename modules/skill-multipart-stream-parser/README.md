# skill-multipart-stream-parser

서블릿 컨테이너나 스프링 프레임워크 의존성 없이 순수 Java 21 InputStream에서 RFC 7578 multipart/form-data 스트림을 직접 파싱하며, 설정 임계치(기본 1MB) 초과 시 임시 디스크 파일로 자동 스왑하고 닫힐 때 자원을 자동 정리하는 경량 파서입니다.

## 주요 기능
- `MultipartStreamParser.parse(in, boundary, threshold)`: 멀티파트 스트림 파싱
- `result.getFieldValue("username")`: 폼 텍스트 값 즉시 추출
- `part.isFile()` / `part.filename()`: 업로드 파일 식별
- `result.close()`: 스왑된 임시 디스크 파일 일괄 자동 삭제
