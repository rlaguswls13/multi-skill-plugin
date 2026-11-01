# 🌐 `skill-http-forwarded-header-parser`

> **순수 Java 21 표준 라이브러리 기반 RFC 7239 `Forwarded` 및 레거시 `X-Forwarded-*` 프록시 헤더 파서 & 정규화기**

- **아티팩트 좌표**: `com.msp:skill-http-forwarded-header-parser:1.0.0`
- **개별 JAR 산출물**: `modules/skill-http-forwarded-header-parser/build/libs/skill-http-forwarded-header-parser-1.0.0.jar`
- **외부 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **RFC 7239 규격 완벽 준수 파싱**:
   - 다중 홉(Multi-hop) 콤마(`,`) 구분 및 세미콜론(`;`) 파라미터 파싱
   - 따옴표 내부 콤마/세미콜론 보호 및 이스케이프 문자(`\"`, `\\`) 정규화
   - 대소문자 무구분 파라미터 처리 (`by`, `for`, `host`, `proto` 및 임의 extension)
2. **IPv6 및 포트 번호 자동 추출**:
   - 브래킷 포맷 IPv6 (`[2001:db8::1]:8080`)에서 순수 IP와 포트 번호 분리 추출
3. **레거시 헤더 (`X-Forwarded-*`) 상호 운용**:
   - `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host`, `X-Forwarded-Port`를 통합 `ForwardedElement`로 정규화
4. **안전한 최외곽 클라이언트 IP 추출 (`getClientIp`)**:
   - RFC 7239 우선 검사 후 레거시 헤더 폴백, 포트/대괄호 제거를 통한 정제된 IP 획득
5. **RFC 7239 직렬화기 (`toRfc7239`)**:
   - 구조화된 엘리먼트 목록을 RFC 7239 표준 포맷으로 재구성

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.forwarded.ForwardedElement;
import com.msp.forwarded.ForwardedHeaderParser;
import java.util.List;

public class ForwardedExample {
    public static void main(String[] args) {
        // 1. RFC 7239 헤더 파싱
        String rfcHeader = "for=192.0.2.43, for=\"[2001:db8::1]:8080\";proto=https;host=\"api.internal:8443\"";
        List<ForwardedElement> elements = ForwardedHeaderParser.parseRfc7239(rfcHeader);

        for (ForwardedElement el : elements) {
            System.out.println("Hop For: " + el.forAddress());
            System.out.println("Clean IP: " + el.forIp());
            el.forPort().ifPresent(port -> System.out.println("Port: " + port));
            System.out.println("Proto: " + el.proto());
        }

        // 2. 안전한 클라이언트 IP 추출
        String clientIp = ForwardedHeaderParser.getClientIp(rfcHeader, "10.0.0.1");
        System.out.println("Outermost Client IP: " + clientIp); // 192.0.2.43

        // 3. RFC 7239 직렬화
        String serialized = ForwardedHeaderParser.toRfc7239(elements);
        System.out.println("Serialized: " + serialized);
    }
}
```
