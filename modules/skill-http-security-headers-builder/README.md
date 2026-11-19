# 📦 `skill-http-security-headers-builder`

> **순수 Java 21 표준 라이브러리 기반 표준 HTTP 보안 응답 헤더 빌더 및 정책 검증기 (HSTS, CSP, X-Frame-Options, Permissions-Policy, COOP, COEP)**

- **아티팩트 좌표**: `com.msp:skill-http-security-headers-builder:1.0.0`
- **개별 JAR 산출물**: `modules/skill-http-security-headers-builder/build/libs/skill-http-security-headers-builder-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **표준 보안 헤더 Fluent Builder**:
   - `Strict-Transport-Security` (HSTS): `maxAge`, `includeSubDomains`, `preload`
   - `Content-Security-Policy` (CSP): `defaultSrc`, `scriptSrc`, `styleSrc`, `imgSrc`, `connectSrc`, `fontSrc`, `objectSrc`, `frameAncestors`, `reportUri` 등
   - `X-Frame-Options`: `DENY`, `SAMEORIGIN` (Clickjacking 방지)
   - `X-Content-Type-Options`: `nosniff` (MIME sniffing 방어)
   - `Referrer-Policy`: `strict-origin-when-cross-origin`, `no-referrer` 등
   - `Permissions-Policy`: `camera`, `microphone`, `geolocation`, `payment` 등 브라우저 하드웨어 API 접근 제어
   - `Cross-Origin-Opener-Policy` (COOP), `Cross-Origin-Embedder-Policy` (COEP), `Cross-Origin-Resource-Policy` (CORP)
2. **보안 프로파일 프리셋**:
   - `SecurityHeadersBuilder.strict()`: 웹 애플리케이션 방어 심층 강화 프리셋
   - `SecurityHeadersBuilder.api()`: JSON REST API 최적화 경량 프리셋
   - `SecurityHeadersBuilder.relaxed()`: 기본 프레임 및 출처 허용 프리셋
3. **HTTP 어댑터 내보내기**:
   - `Map<String, String> toHeadersMap()`
   - `applyTo(BiConsumer<String, String> headerConsumer)`: Spring `HttpServletResponse`, Javalin, Ktor, Netty, Vert.x 등과 즉시 연동
4. **보안 정책 검증기 (`SecurityPolicyValidator`)**:
   - CSP 내 nonce/hash 없는 `unsafe-inline` 및 와일드카드(`*`) 스크립트 허용 탐지
   - HSTS 누락 및 브라우저 프리로드 요건(1년 미만 만료, subDomains 누락) 오류 감지
   - 클릭재킹 방어(XFO / CSP frame-ancestors) 누락 경고
   - Referrer 누출(`unsafe-url`) 탐지

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.secheaders.*;

public class Example {
    public static void main(String[] args) {
        // 1. 프리셋 또는 Fluent Builder로 보안 헤더 구성
        SecurityHeaders headers = SecurityHeaders.builder()
                .hsts(HstsConfig.defaultStrict())
                .csp(csp -> csp
                        .defaultSrc("'self'")
                        .scriptSrc("'self'", "https://cdn.example.com")
                        .styleSrc("'self'", "https://fonts.googleapis.com")
                        .fontSrc("'self'", "https://fonts.gstatic.com")
                        .frameAncestors("'none'")
                        .upgradeInsecureRequests())
                .xFrameOptions(XFrameOptions.DENY)
                .contentTypeOptionsNosniff(true)
                .referrerPolicy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                .permissionsPolicy(p -> p
                        .geolocation()
                        .camera()
                        .microphone())
                .coop(CoopPolicy.SAME_ORIGIN)
                .coep(CoepPolicy.REQUIRE_CORP)
                .build();

        // 2. 정책 사전 검증
        SecurityPolicyValidator.ValidationReport report = SecurityPolicyValidator.validate(headers);
        if (!report.isValid()) {
            System.err.println("Security violations found: " + report.issues());
        }

        // 3. 서블릿 / 웹 프레임워크 응답에 적용
        // headers.applyTo(response::setHeader);
        headers.toHeadersMap().forEach((k, v) -> System.out.println(k + ": " + v));
    }
}
```
