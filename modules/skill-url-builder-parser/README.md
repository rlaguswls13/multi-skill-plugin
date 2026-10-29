# 📦 `skill-url-builder-parser`

> **외부 라이브러리 없이 순수 Java 21 표준 라이브러리로 유연한 URL 빌더(Fluent Builder), 경로 결합기(Path Combiner), 쿼리 파라미터 멀티맵 인코딩/디코딩, 그리고 RFC 3986 기반 URL 정규화(Normalizer)를 제공하는 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-url-builder-parser:1.0.0`
- **개별 JAR 산출물**: `modules/skill-url-builder-parser/build/libs/skill-url-builder-parser-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **`UrlBuilder` (Fluent URL 빌더)**:
   - 프로토콜(`http()`, `https()`), 호스트, 포트, 사용자 인증 정보(`userInfo`), 프래그먼트 빌드
   - `appendPath(...)`: 슬래시 중복(`//`) 또는 누락 없이 자연스러운 경로 세그먼트 결합
   - `queryParam`, `queryParams`, `addQueryParams`: 단일 및 다중 쿼리 파라미터 유연한 조작
   - `from(urlString)`: 기존 URL을 읽어와 즉시 수정 및 재생성 가능
2. **`QueryParams` (다중값 쿼리 파라미터 맵)**:
   - 동일 키에 다중 값 지원 (`tag=java&tag=spring`)
   - RFC 3986 표준 퍼센트 인코딩 및 디코딩 지원
   - `toSingleValueMap()`, `toMultiValueMap()` 변환 유틸리티
3. **`UrlParser` & `ParsedUrl`**:
   - URL 분해 및 파싱: 절대 URL, 스킴 생략 프로토콜 상대 URL(`//host/path`), 상대 경로 URL 완벽 지원
4. **`UrlNormalizer`**:
   - RFC 3986 기반 URL 정규화:
     - 스킴 및 호스트 소문자화
     - 기본 포트(http 80, https 443, ftp 21 등) 자동 제거
     - 경로 내 점 세그먼트(`.`, `..`) 및 중복 슬래시(`//`) 자동 정리
     - 비예약(Unreserved) 퍼센트 인코딩(`%7E` ➔ `~`) 자동 해제
     - 쿼리 파라미터 키/값 사전순 정렬

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.url.*;

// 1. Fluent URL 빌더로 API 요청 URL 생성
String apiUrl = UrlBuilder.create()
        .https()
        .host("api.service.com")
        .path("/v1")
        .appendPath("users", "42", "orders")
        .queryParam("status", "COMPLETED")
        .addQueryParam("tag", "electronics")
        .addQueryParam("tag", "discounted")
        .fragment("summary")
        .build();
// 결과: "https://api.service.com/v1/users/42/orders?status=COMPLETED&tag=electronics&tag=discounted#summary"

// 2. 기존 URL 수정
String modifiedUrl = UrlBuilder.from(apiUrl)
        .queryParam("status", "PENDING")
        .build();

// 3. URL 파싱
ParsedUrl parsed = UrlParser.parse(apiUrl);
System.out.println("Host: " + parsed.host()); // "api.service.com"
System.out.println("Tags: " + parsed.queryParams().getAll("tag")); // [electronics, discounted]

// 4. URL 정규화 (포트 제거, 경로 상대참조 해석, 소문자화, 쿼리 정렬)
String rawUrl = "HTTP://EXAMPLE.COM:80/a/b/../c?z=1&a=2";
String normalized = UrlNormalizer.normalize(rawUrl);
// 결과: "http://example.com/a/c?a=2&z=1"
```
