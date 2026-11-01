# 🌐 `skill-http-cors-preflight-evaluator`

> **W3C CORS & Fetch 명세 준수 HTTP Preflight (OPTIONS) 및 본 요청(Actual Request) 검증 엔진**

- **아티팩트 좌표**: `com.msp:skill-http-cors-preflight-evaluator:1.0.0`
- **개별 JAR 산출물**: `modules/skill-http-cors-preflight-evaluator/build/libs/skill-http-cors-preflight-evaluator-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **W3C CORS & Fetch 명세 완벽 준수**:
   - `OPTIONS` 프리플라이트 요청(`Origin`, `Access-Control-Request-Method`, `Access-Control-Request-Headers`) 검증
   - 본 요청(Actual Request)에 대한 오리진 검증 및 안전한 응답 헤더 생성
2. **엄격한 보안 규격 준수 (W3C Security Guard)**:
   - `allowCredentials: true` 설정 시 와일드카드 `*` 출력을 엄격히 금지하고, 인가된 요청 오리진을 안전하게 반사(Reflect)
3. **다양한 오리진 매칭 지원**:
   - 정확한 오리진 일치(`https://service.com`)
   - 와일드카드 서브도메인 글로브 패턴(`https://*.example.com`) 및 정규식 패턴 지원
   - 전체 허용(`*`)
4. **Chrome PNA (Private Network Access) 지원**:
   - `Access-Control-Request-Private-Network` 요청 및 `Access-Control-Allow-Private-Network` 응답 지원
5. **독립된 순수 Java 21**:
   - 서블릿, 스프링, 네티 등 특정 웹 프레임워크에 종속되지 않고 어떤 HTTP 서버/게이트웨이에서도 즉시 사용 가능

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.cors.CorsConfig;
import com.msp.cors.CorsEvaluator;
import com.msp.cors.CorsRequest;
import com.msp.cors.CorsEvaluationResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class CorsExample {
    public static void main(String[] args) {
        // 1. CORS 정책 정의
        CorsConfig config = CorsConfig.builder()
                .allowOriginPattern("https://*.company.com")
                .allowMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowHeaders("Authorization", "Content-Type", "X-Requested-With")
                .exposeHeaders("X-Total-Count", "X-Trace-Id")
                .allowCredentials(true)
                .maxAge(Duration.ofHours(2))
                .build();

        CorsEvaluator evaluator = new CorsEvaluator(config);

        // 2. 프리플라이트 (OPTIONS) 요청 평가
        CorsRequest preflightReq = CorsRequest.preflight(
                "https://admin.company.com",
                "POST",
                List.of("Authorization", "Content-Type")
        );

        CorsEvaluationResult result = evaluator.evaluate(preflightReq);

        if (result.isAllowed()) {
            System.out.println("프리플라이트 통과! 응답 헤더:");
            result.getResponseHeaders().forEach((header, value) -> {
                System.out.println("  " + header + ": " + value);
            });
            // Access-Control-Allow-Origin: https://admin.company.com
            // Access-Control-Allow-Credentials: true
            // Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
            // Access-Control-Allow-Headers: Authorization, Content-Type
            // Access-Control-Max-Age: 7200
        } else {
            System.err.println("CORS 거부 사유: " + result.getRejectionReason().orElse("Unknown"));
        }
    }
}
```
