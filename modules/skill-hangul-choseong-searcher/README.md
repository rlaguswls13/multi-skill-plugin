# 📦 `skill-hangul-choseong-searcher`

> **외부 라이브러리 없이 순수 Java 21 표준 라이브러리로 한국어 한글 음절 자모 분해(초성, 중성, 종성) 및 초성 검색, 실시간 자동완성 매칭, 검색어 하이라이트를 지원하는 경량 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-hangul-choseong-searcher:1.0.0`
- **개별 JAR 산출물**: `modules/skill-hangul-choseong-searcher/build/libs/skill-hangul-choseong-searcher-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **`HangulDecomposer` (한글 자모 분해기)**:
   - 유니코드 음절 영역(U+AC00 ~ U+D7A3, 11,172자)을 초성 19자, 중성 21자, 종성 28자(받침 없음 포함)로 즉각 분해.
   - 겹받침(`닭` ➔ `ㄷ`, `ㅏ`, `ㄺ`), 받침 유무 판별(`hasJongseong()`).
   - `extractChoseong(text)`: 한글만 초성으로 치환하고 영문/숫자/기호/한자는 그대로 보존.
2. **`ChoseongSearcher` (초성 검색 & 실시간 자동완성 엔진)**:
   - **순수 초성 검색**: `"홍길동"` ➔ `"ㅎㄱㄷ"` 완전 일치.
   - **부분 문자열 검색**: `"홍길동전"` ➔ `"ㄱㄷ"` 부분 일치.
   - **혼합 검색(초성 + 완성형)**: `"홍길동전"` ➔ `"홍ㄱㄷ"` 일치.
   - **영문/숫자/한자 혼합 및 대소문자 무시**: `"Java개발자"` ➔ `"javaㄱㅂ"` 매칭.
   - **입력 중(In-Flight) 접두사 매칭**: 사용자가 타이핑 중인 미완성 글자(`"홍기"`)로 완성 단어(`"홍길동"`) 실시간 매칭.
3. **검색 결과 범위 탐색 및 하이라이트**:
   - `search(text, query)`: 매칭된 위치 인덱스(`MatchRange(start, end, matchedText)`) 반환.
   - `highlight(text, query, preTag, postTag)`: HTML `<mark>` 태그 등으로 매칭 키워드 원본 보존 래핑.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.hangul.*;

import java.util.List;

public class Example {
    public static void main(String[] args) {
        // 1. 자모 분해
        HangulSyllable s = HangulDecomposer.decompose('홍');
        System.out.println(s.choseong());  // 'ㅎ'
        System.out.println(s.jungseong()); // 'ㅗ'
        System.out.println(s.jongseong()); // 'ㅇ'

        // 2. 초성 문자열 일괄 추출
        String cho = HangulDecomposer.extractChoseong("홍길동 2026 Java!");
        System.out.println(cho); // "ㅎㄱㄷ 2026 Java!"

        // 3. 초성 및 혼합 검색
        boolean match1 = ChoseongSearcher.matches("홍길동전", "ㅎㄱㄷ");    // true
        boolean match2 = ChoseongSearcher.matches("삼성전자 갤럭시", "삼성ㅈㅈ"); // true
        boolean match3 = ChoseongSearcher.matches("iPhone 15 프로", "15 ㅍㄹ"); // true

        // 4. 타이핑 중 자동완성 매칭 ("홍기" -> "홍길동")
        boolean inFlight = ChoseongSearcher.matches("홍길동", "홍기"); // true

        // 5. 검색어 하이라이트
        String highlighted = ChoseongSearcher.highlight(
                "홍길동전은 허균의 소설이며, 홍길동이 주인공입니다.",
                "ㅎㄱㄷ",
                "<mark>",
                "</mark>"
        );
        System.out.println(highlighted);
        // "<mark>홍길동</mark>전은 허균의 소설이며, <mark>홍길동</mark>이 주인공입니다."
    }
}
```
