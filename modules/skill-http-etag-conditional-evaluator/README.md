# HTTP ETag & Conditional Request Evaluator (`skill-http-etag-conditional-evaluator`)

Zero-dependency Java 21 implementation of RFC 7232 HTTP conditional request evaluation (`If-Match`, `If-None-Match`, `If-Modified-Since`, `If-Unmodified-Since`).

## Key Features

- **RFC 7232 Section 2.3.2 Comparison Rules**:
  - **Strong Comparison**: Both tags must be strong (no `W/` prefix) and opaque values byte-identical. Used for `If-Match` and state-changing `If-None-Match` (PUT/POST/DELETE).
  - **Weak Comparison**: Opaque values match regardless of `W/` weakness indicator. Used for GET/HEAD caching (`If-None-Match`).
- **RFC 7232 Section 6 Strict Evaluation Precedence**:
  1. `If-Match` &rarr; 412 on mismatch; skips `If-Unmodified-Since` on match.
  2. `If-Unmodified-Since` &rarr; 412 if modified after timestamp.
  3. `If-None-Match` &rarr; 304 (GET/HEAD) or 412 (others) on match; skips `If-Modified-Since` on mismatch.
  4. `If-Modified-Since` &rarr; 304 if resource not modified since timestamp (GET/HEAD only).
  5. Preconditions passed &rarr; 200 OK.
- **Robust HTTP-Date Parser**:
  - RFC 1123 / RFC 822 (`Sun, 06 Nov 1994 08:49:37 GMT`)
  - RFC 850 (`Sunday, 06-Nov-94 08:49:37 GMT`)
  - ANSI C `asctime()` (`Sun Nov  6 08:49:37 1994`)

## Runnable Code Example

```java
import com.msp.etag.ConditionalEvaluationResult;
import com.msp.etag.EntityTag;
import com.msp.etag.HttpConditionalEvaluator;
import java.time.Instant;
import java.util.Map;

public class EtagExample {
    public static void main(String[] args) {
        HttpConditionalEvaluator evaluator = HttpConditionalEvaluator.create()
                .withEtag(EntityTag.strong("686897696a7c876b7e"))
                .withLastModified(Instant.parse("2026-11-01T10:00:00Z"));

        // Scenario 1: Client sends If-None-Match for cache validation
        Map<String, String> getHeaders = Map.of(
                "If-None-Match", "W/\"686897696a7c876b7e\""
        );
        ConditionalEvaluationResult result = evaluator.evaluate(getHeaders, "GET");
        System.out.println("Status: " + result.getStatusCode()); // 304
        System.out.println("Passed: " + result.isPassed());       // false

        // Scenario 2: Optimistic locking for PUT
        Map<String, String> putHeaders = Map.of(
                "If-Match", "\"old-version-etag\""
        );
        ConditionalEvaluationResult putResult = evaluator.evaluate(putHeaders, "PUT");
        System.out.println("Status: " + putResult.getStatusCode()); // 412 Precondition Failed
    }
}
```
