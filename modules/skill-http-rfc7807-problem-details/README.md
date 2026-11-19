# 📦 `skill-http-rfc7807-problem-details`

> **RFC 7807 & RFC 9457 HTTP Problem Details Builder, Serializer & Parser in Pure Java 21**

- **아티팩트 좌표**: `com.msp:skill-http-rfc7807-problem-details:1.0.0`
- **개별 JAR 산출물**: `modules/skill-http-rfc7807-problem-details/build/libs/skill-http-rfc7807-problem-details-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **RFC 7807 & RFC 9457 표준 스펙 준수**:
   - `type`: 문제 유형을 식별하는 URI (미지정 시 기본값 `about:blank`)
   - `title`: 문제 유형에 대한 요약 설명 (HTTP 상태 코드별 표준 Reason Phrase 자동 매핑)
   - `status`: HTTP 상태 코드 (e.g. 400, 404, 500)
   - `detail`: 이번 특정 발생 건에 대한 상세 설명
   - `instance`: 특정 오류 발생 인스턴스 URI
2. **확장 멤버(Extension Members) 지원**:
   - `traceId`, `errorCode`, `timestamp`, `invalidParams` 등 임의의 커스텀 필드 자유롭게 확장
   - 필드 검증 오류 전용 헬퍼 `invalidParam(name, reason)` 제공
3. **가볍고 빠른 Pure Java JSON 코덱**:
   - 외부 무거운 라이브러리(Jackson, Gson) 없이 순수 Java 21로 직렬화 및 파싱 지원
   - 유니코드 이스케이프, 특수 문자, 중첩 객체/배열 완벽 처리
4. **편의 팩토리 & 예외 래퍼**:
   - `ProblemDetails.badRequest(detail)`
   - `ProblemDetails.notFound(detail)`
   - `ProblemDetails.internalServerError(detail)`
   - `ProblemDetailsException` 런타임 예외 클래스 제공

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.problemdetails.ProblemDetails;
import java.net.URI;

public class ProblemDetailsExample {
    public static void main(String[] args) {
        // 1. Fluent Builder로 Problem Details 객체 생성
        ProblemDetails problem = ProblemDetails.builder()
                .type(URI.create("https://example.com/probs/validation"))
                .status(400)
                .title("Validation Failed")
                .detail("One or more fields failed validation.")
                .instance(URI.create("/api/v1/users/create"))
                .extension("traceId", "req-abcdef-123456")
                .invalidParam("email", "Must be a valid email format")
                .invalidParam("age", "Must be positive integer")
                .build();

        // 2. JSON 직렬화 (RFC 7807)
        String json = problem.toJson();
        System.out.println(json);

        // 3. JSON 역직렬화 (Parsing)
        ProblemDetails parsed = ProblemDetails.fromJson(json);
        System.out.println("Status: " + parsed.getStatus());
        System.out.println("Trace ID: " + parsed.getExtension("traceId"));
    }
}
```
