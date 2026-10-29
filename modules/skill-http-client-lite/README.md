# 🌐 `skill-http-client-lite`

> **외부 프레임워크 의존성 없이 Java 21 표준 `java.net.http.HttpClient`를 래핑한 플루언트(Fluent) HTTP 클라이언트 라이브러리**

- **아티팩트 좌표**: `com.msp:skill-http-client-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-http-client-lite/build/libs/skill-http-client-lite-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **플루언트 요청 빌더**: GET, POST, PUT, DELETE, PATCH, HEAD 메서드 지원.
2. **헤더 & 인증 관리**: Bearer 토큰 간편 설정, Content-Type, 커스텀 다중 헤더.
3. **쿼리 파라미터 직렬화**: URL 인코딩 자동 처리 및 다중 파라미터 바인딩.
4. **JSON 페이로드**: `.jsonBody(jsonString)` 지원 및 응답 바디 문자열/바이트 추출.
5. **자동 재시도(Automatic Retry)**: 5xx 서버 에러 또는 일시적 통신 실패 시 재시도 횟수 및 딜레이 설정.
6. **타임아웃 & 비동기 지원**: Connect 및 Request Timeout 제어, Virtual Threads 기반 `executeAsync()` 비동기 반환.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.http.*;
import java.time.Duration;

// 1. 간편 GET 요청 (HttpClients 파사드)
LiteHttpResponse getRes = HttpClients.get("https://api.example.com/users")
    .queryParam("page", 1)
    .queryParam("limit", 20)
    .bearerToken("my-jwt-token")
    .execute();

if (getRes.isSuccess()) {
    System.out.println("응답 본문: " + getRes.body());
}

// 2. 재시도 및 타임아웃이 적용된 커스텀 클라이언트 POST JSON 요청
LiteHttpClient client = LiteHttpClient.builder()
    .connectTimeout(Duration.ofSeconds(5))
    .defaultMaxRetries(3)
    .defaultRetryDelay(Duration.ofMillis(300))
    .build();

LiteHttpResponse postRes = client.post("https://api.example.com/orders")
    .jsonBody("{\"productId\": 101, \"quantity\": 2}")
    .timeout(Duration.ofSeconds(3))
    .execute();

System.out.println("HTTP 상태 코드: " + postRes.statusCode());

// 3. 비동기 실행 (CompletableFuture)
client.get("https://api.example.com/status")
    .executeAsync()
    .thenAccept(res -> System.out.println("비동기 수신: " + res.body()));
```
