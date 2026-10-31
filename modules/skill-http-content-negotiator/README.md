# 📦 `skill-http-content-negotiator`

> **순수 Java 21 표준 라이브러리 기반 RFC 7231 HTTP Content-Type, Language, Encoding 품질 계수(q-value) 협상 엔진**

- **아티팩트 좌표**: `com.msp:skill-http-content-negotiator:1.0.0`
- **개별 JAR 산출물**: `modules/skill-http-content-negotiator/build/libs/skill-http-content-negotiator-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **RFC 7231 품질 계수(Quality Factor, `q=...`) 파싱**:
   - `Accept`, `Accept-Language`, `Accept-Encoding` 헤더의 `q` 가중치(0.0 ~ 1.0) 및 부가 파라미터(`charset`, `level`) 자동 추출
2. **MIME 타입 세분성(Specificity) 기반 협상**:
   - 동일 가중치일 경우 RFC 7231에 따라 `type/subtype` > `type/*` > `*/*` 우선순위 자동 결정
3. **다국어 서브태그 폴백 및 접두사 매칭**:
   - `en-US` 요청 시 `en` 지원 서버 매칭 (Subtag fallback)
   - `en` 요청 시 `en-GB` 지역화 언어 매칭 (Prefix match)
4. **와일드카드 및 미지원 거부 처리**:
   - `*/*`, `*` 와일드카드 처리 및 `q=0.0` 명시적 거부 처리

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.contentneg.ContentNegotiator;
import java.util.List;

public class ContentNegExample {
    public static void main(String[] args) {
        // 1. Content-Type 협상
        String acceptHeader = "text/html;q=0.5, application/json;q=0.9, */*;q=0.1";
        List<String> supportedTypes = List.of("text/html", "application/json");

        String selectedType = ContentNegotiator.negotiateContentType(acceptHeader, supportedTypes);
        System.out.println("Selected MIME: " + selectedType); // application/json

        // 2. 언어 협상 (Subtag fallback)
        String langHeader = "ko-KR, ko;q=0.9, en-US;q=0.8";
        List<String> supportedLangs = List.of("en", "fr");

        String selectedLang = ContentNegotiator.negotiateLanguage(langHeader, supportedLangs);
        System.out.println("Selected Lang: " + selectedLang); // en

        // 3. 인코딩 압축 협상
        String encodingHeader = "gzip;q=0.6, br;q=1.0, deflate;q=0.5";
        List<String> supportedEncodings = List.of("gzip", "br");

        String selectedEncoding = ContentNegotiator.negotiateEncoding(encodingHeader, supportedEncodings);
        System.out.println("Selected Encoding: " + selectedEncoding); // br
    }
}
```
