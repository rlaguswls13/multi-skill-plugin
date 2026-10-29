# 📦 `skill-text-truncator`

> **외부 라이브러리 없이 순수 Java 21 표준 라이브러리로 글자 수(Character Count), 바이트 수(UTF-8, EUC-KR, MS949) 및 단어 수 기준 안전한 텍스트 축약(Truncation)을 수행하며 다국어 깨짐 및 유니코드 서로게이트 분리를 원천 차단하는 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-text-truncator:1.0.0`
- **개별 JAR 산출물**: `modules/skill-text-truncator/build/libs/skill-text-truncator-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **글자 수(Length) 안전 자르기**:
   - `truncateByLength(text, maxLength, ellipsis)`: 말줄임표(`...`)를 포함하여 정확히 지정한 글자 수 이내로 자름.
   - **이모지 서러게이트(Surrogate Pair) 분리 방어**: 4바이트 이모지(`🚀`, `🎉`)의 상위 서러게이트만 잘려나가는 유니코드 손상을 원천 방지.
2. **바이트 단위(Byte-Safe) 자르기 (UTF-8, EUC-KR)**:
   - SMS/LMS 전송, RDBMS 바이트 컬럼 제한(VARCHAR2 등), 레거시 전문 통신 시 필수.
   - `truncateByBytes(text, maxBytes, Charset, ellipsis)`: 한글(UTF-8 3바이트, EUC-KR 2바이트)이 바이트 경계에서 반쪽만 잘려 `\uFFFD`나 `?`로 깨지는 현상을 100% 방지.
3. **단어 보존(Word Preservation) 모드**:
   - `truncateByLengthPreservingWords`, `truncateByBytesPreservingWords`: 단어 중간이 어색하게 끊기지 않도록 직전 공백을 찾아 단어 단위로 깔끔하게 자름.
4. **단어 수(Word Count) 기준 자르기**:
   - `truncateWords(text, maxWords, ellipsis)`: 띄어쓰기 기준 지정한 단어 개수만큼만 유지.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.truncate.TextTruncator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Example {
    public static void main(String[] args) {
        // 1. 글자 수 자르기
        String s1 = TextTruncator.truncateByLength("Hello, Wonderful World!", 12);
        System.out.println(s1); // "Hello, Wo..."

        // 2. 단어 보존 자르기
        String s2 = TextTruncator.truncateByLengthPreservingWords("The quick brown fox jumps", 15, "...");
        System.out.println(s2); // "The quick..."

        // 3. UTF-8 바이트 안전 자르기 (한글 깨짐 방지)
        String s3 = TextTruncator.truncateByBytes("대한민국만세 무궁화삼천리", 14, StandardCharsets.UTF_8, "...");
        System.out.println(s3); // "대한민국..." (UTF-8 14바이트 이내 안전 수렴)

        // 4. EUC-KR / 레거시 SMS 바이트 자르기
        Charset eucKr = Charset.forName("EUC-KR");
        String s4 = TextTruncator.truncateByBytes("주식회사 멀티스킬플러그인", 16, eucKr, "..");
        System.out.println(s4); // "주식회사 멀티.."

        // 5. 단어 개수 자르기
        String s5 = TextTruncator.truncateWords("인공지능 기반 자바 모듈형 아키텍처 구현", 3);
        System.out.println(s5); // "인공지능 기반 자바..."
    }
}
```
