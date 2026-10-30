# HTTP ETag Generator & Matcher (`skill-etag-generator`)

RFC 7232 준수 HTTP ETag (Entity Tag) 강한 검증기(SHA-256) 및 약한 검증기(W/) 생성기 및 `If-None-Match` 조건부 캐시 매칭 유틸리티입니다.

## Features
- **Strong ETag**: SHA-256 해시 기반 완벽한 콘텐츠 무결성 ETag 생성 (`"hex..."`).
- **Weak ETag**: 리소스 크기 및 마지막 수정 시각(Last-Modified) 기반 경량 약한 ETag 생성 (`W/"length-mtime"`).
- **RFC 7232 Cache Matcher**: `If-None-Match` 헤더(다중 태그, 와일드카드 `*`, 약한 비교) 자동 검증으로 HTTP 304 Not Modified 판별.
- **Zero Dependencies**: 순수 Java 21 표준 라이브러리(`MessageDigest`, `HexFormat`).

## Usage

```java
// 1. 강한 ETag 생성
Etag etag = EtagGenerator.generateStrong("Hello API response");
response.setHeader("ETag", etag.toHeaderValue());

// 2. If-None-Match 조건부 검사
String ifNoneMatch = request.getHeader("If-None-Match");
if (EtagMatcher.matchesIfNoneMatch(ifNoneMatch, etag)) {
    response.setStatus(304); // Not Modified
    return;
}
```
