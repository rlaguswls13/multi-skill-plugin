# skill-http-cookie-jar-rfc6265

RFC 6265 표준 규격을 완벽히 준수하는 순수 Java 21 기반의 인메모리 HTTP `CookieJar` 모듈입니다.

외부 라이브러리(Apache HttpClient, OkHttp 등) 의존성 없이 JDK 표준 라이브러리만으로 `SameSite`, `Domain` 서브도메인 매칭, `Path` 매칭, 만료(`Max-Age`, `Expires`), `Secure`, `HttpOnly` 검증 및 정렬된 `Cookie` 헤더 생성을 지원합니다.

---

## 🚀 주요 기능

- **RFC 6265 Domain Matching**: 동일 도메인 및 `.domain.com` 서브도메인 매칭, IP 주소 분기 처리 및 `Host-Only` 쿠키 격리
- **RFC 6265 Path Matching**: 경로 접두어 및 하위 디렉터리(`/`) 구분 검증
- **RFC 6265bis SameSite**: `Strict`, `Lax`, `None` 지원
- **우선순위 만료 계산**: `Max-Age` 우선 적용, RFC 1123 / RFC 850 / asctime 날짜 파싱 지원
- **RFC 6265 정렬 규격 준수**: `Cookie` 요청 헤더 생성 시 경로 길이가 긴 쿠키 우선 정렬 및 생성 시간순 정렬
- **스레드 세이프(Thread-Safe)**: `ReentrantReadWriteLock` 기반 동시성 제어

---

## 📦 사용 예시

```java
import com.msp.cookiejar.CookieJar;
import com.msp.cookiejar.HttpCookieRecord;
import com.msp.cookiejar.SameSite;

import java.net.URI;
import java.util.List;

public class CookieJarExample {
    public static void main(String[] args) {
        CookieJar cookieJar = new CookieJar();

        URI serverUri = URI.create("https://sub.example.com/api/v1/users");

        // Set-Cookie 응답 헤더 추가
        cookieJar.add(serverUri, "sessionId=auth_token_123; Domain=example.com; Path=/api; Secure; HttpOnly; SameSite=Strict");
        cookieJar.add(serverUri, "theme=dark; Path=/api/v1; Max-Age=3600");

        // URI에 해당하는 Cookie 요청 헤더 생성 (경로가 더 긴 /api/v1 이 먼저 정렬됨)
        String cookieHeader = cookieJar.getCookieHeader(serverUri);
        System.out.println("Cookie: " + cookieHeader);
        // 출력: "theme=dark; sessionId=auth_token_123"

        // 쿠키 리스트 조회
        List<HttpCookieRecord> cookies = cookieJar.getCookies(serverUri);
        for (HttpCookieRecord cookie : cookies) {
            System.out.printf("Name: %s, Value: %s, Secure: %b%n", cookie.name(), cookie.value(), cookie.secure());
        }

        // 만료된 쿠키 일괄 정리
        cookieJar.purgeExpired();
    }
}
```

---

## 🧪 테스트 검증 매트릭스

- `testBasicCookieParsing`: `Set-Cookie` 기본 속성(이름, 값, 기본 경로, 플래그) 파싱 검증
- `testDomainMatching`: 상위 도메인 공유, 서브도메인 매칭, IP 주소 제외, Host-Only 쿠키 격리 검증
- `testPathMatching`: 하위 디렉터리 매칭, 비경로 일치 차단 검증
- `testExpiration`: `Max-Age` 만료 및 `Max-Age=0` 즉시 삭제 검증
- `testSecureFlag`: 비보안 HTTP 요청 시 전송 차단 검증
- `testSameSiteParsing`: `Strict`, `Lax`, `None` 파싱 검증
- `testPathSortOrderInHeader`: RFC 6265 섹션 5.4 경로 길이 역순 정렬 검증
- `testConcurrentOperations`: 다중 스레드 환경 동시 추가/조회 무결성 검증
