# URI Template Matcher (`skill-url-template-matcher`)

Spring / RFC 6570 스타일의 URI 템플릿 매칭, 경로 변수 추출(Variable Extraction), 템플릿 확장(Expansion)을 지원하는 순수 Java 21 표준 라이브러리 유틸리티입니다.

외부 웹 프레임워크(Spring Web, JAX-RS 등) 의존성 없이 독립형 서버, API 게이트웨이, 라우터, 마이크로서비스에서 가볍게 즉시 사용할 수 있습니다.

## Features

- **단일 세그먼트 변수 추출**: `/api/users/{userId}/orders/{orderId}` 포맷 지원
- **와일드카드 다중 세그먼트 추출**:
  - RFC 6570 스타일: `/files/{path:*}`
  - Spring 스타일: `/docs/{*path}`
  - 범용 접미사 와일드카드: `/assets/{path*}`
- **리터럴 와일드카드**: 단일 세그먼트(`*`), 다중 세그먼트(`**`) 패턴 매칭 지원
- **URI 정규화 지원**: 쿼리 스트링(`?query=...`), 프래그먼트(`#hash`), 전체 스키마(`http://...`)가 포함된 URI도 자동 경로 추출
- **템플릿 변수 치환(Expansion)**: 변수 맵을 이용한 안전하고 빠른 URI 문자열 완성
- **사전 컴파일 캐시 및 스레드 세이프**: 불변 객체 설계 및 `ConcurrentHashMap` 기반 패턴 캐싱 지원
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리(`java.util.regex`, `java.net.URI`)만 사용

## Usage

```java
import com.msp.uritemplate.UriTemplateMatcher;
import java.util.Map;
import java.util.Optional;

// 1. 단일/다중 경로 변수 매칭 및 추출
String template = "/api/users/{userId}/orders/{orderId}";
String uri = "/api/users/user-101/orders/ord-909?page=1";

Optional<Map<String, String>> variables = UriTemplateMatcher.match(template, uri);
if (variables.isPresent()) {
    System.out.println("User ID: " + variables.get().get("userId"));   // user-101
    System.out.println("Order ID: " + variables.get().get("orderId")); // ord-909
}

// 2. 와일드카드 다중 세그먼트 경로 추출 (RFC 6570 style)
String wildcardTemplate = "/files/{path:*}";
Optional<Map<String, String>> fileMatch = UriTemplateMatcher.match(wildcardTemplate, "/files/uploads/2026/report.pdf");
System.out.println("Path: " + fileMatch.get().get("path")); // uploads/2026/report.pdf

// 3. 템플릿 확장 (Expansion)
String expanded = UriTemplateMatcher.expand(
    "/api/tenants/{tenantId}/users/{userId}",
    Map.of("tenantId", "corp-a", "userId", 12345)
);
System.out.println("Expanded URI: " + expanded);
// 출력: /api/tenants/corp-a/users/12345
```
