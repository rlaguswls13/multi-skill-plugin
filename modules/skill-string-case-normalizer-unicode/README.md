# 📦 `skill-string-case-normalizer-unicode`

> **순수 Java 21 표준 라이브러리 기반 Unicode Case Folding 다국어 대소문자 무관 비교 및 정규화기**

- **아티팩트 좌표**: `com.msp:skill-string-case-normalizer-unicode:1.0.0`
- **개별 JAR 산출물**: `modules/skill-string-case-normalizer-unicode/build/libs/skill-string-case-normalizer-unicode-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **표준 Unicode Case Folding (Full Folding)**:
   - 독일어 에스체트(`'ß'` 및 대문자 `'ẞ'`)를 `"ss"`로 확장하여 `"Straße"`와 `"STRASSE"`를 동등하게 인식
   - 그리스어 최종 시그마(`'ς'`), 대문자 시그마(`'Σ'`), 일반 소문자 시그마(`'σ'`)를 단일 소문자 `'σ'`로 통합
   - 타이포그래피 리거처 합자(`'ﬀ'`, `'ﬁ'`, `'ﬂ'`, `'ﬃ'`, `'ﬄ'`, `'ﬅ'`, `'ﬆ'`)를 개별 알파벳 문자열로 정규화
2. **특수 문자 및 단위 기호 정규화**:
   - 롱 에스(`'ſ'` &rarr; `'s'`), 마이크로 기호(`'µ'` &rarr; `'μ'`), 켈빈 기호(`'K'` &rarr; `'k'`), 옹스트롬(`'Å'` &rarr; `'å'`)
3. **고속 ASCII Fast-Path**:
   - 순수 영문 ASCII 문자열은 힙 할당을 최소화한 고속 O(N) 순회로 분기하여 높은 Throughput 제공
4. **다국어 검색 헬퍼 제공**:
   - `equalsIgnoreCase`, `containsIgnoreCase`, `startsWithIgnoreCase`, `endsWithIgnoreCase`

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.casefold.UnicodeCaseFolder;

public class CaseFoldExample {
    public static void main(String[] args) {
        // 1. 독일어 Sharp S 비교
        boolean matchGerman = UnicodeCaseFolder.equalsIgnoreCase("Groß", "GROSS");
        System.out.println("Groß == GROSS ? " + matchGerman); // true

        // 2. 그리스어 오디세우스 (최종 시그마 vs 일반 시그마) 비교
        boolean matchGreek = UnicodeCaseFolder.equalsIgnoreCase("ὈΔΥΣΣΕΎΣ", "ὀδυσσεύς");
        System.out.println("Greek Odyssey match ? " + matchGreek); // true

        // 3. 타이포그래피 합자 포함 검색
        String document = "Welcome to the corporate oﬃce headquarters.";
        boolean found = UnicodeCaseFolder.containsIgnoreCase(document, "OFFICE");
        System.out.println("Contains 'OFFICE' ? " + found); // true

        // 4. Case Folding 문자열 변환
        String folded = UnicodeCaseFolder.fold("Straße & 100K");
        System.out.println("Folded: " + folded); // "strasse & 100k"
    }
}
```
