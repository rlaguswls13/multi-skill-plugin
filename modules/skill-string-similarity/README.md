# 📦 `skill-string-similarity`

> **외부 라이브러리 없이 순수 Java 21 표준 라이브러리로 문자열 유사도 알고리즘(Levenshtein Distance, Jaro-Winkler, N-gram Cosine)과 오타 자동 교정 및 후보 추천(TypoSuggester)을 제공하는 고성능 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-string-similarity:1.0.0`
- **개별 JAR 산출물**: `modules/skill-string-similarity/build/libs/skill-string-similarity-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **`LevenshteinDistance` (편집 거리 및 유사도)**:
   - 두 문자열 간의 삽입, 삭제, 치환 연산의 최소 횟수 계산.
   - O(min(m, n)) 공간 복잡도로 메모리 할당 최소화.
   - `0.0` (완전 불일치) ~ `1.0` (완전 일치) 정규화 유사도 점수 반환.
2. **`JaroWinklerDistance` (단어/인명/오타 유사도)**:
   - 문자 위치 일치 및 치환 전이(Transposition) 계산.
   - 최대 4자 접두사 일치 시 가산 보너스(Prefix bonus) 부여로 인명 및 짧은 단어 오타 매칭에 최적화.
3. **`CosineSimilarity` (N-gram 코사인 유사도)**:
   - 문자 단위 n-gram (기본 2-gram/bigram) 빈도 벡터 기반 코사인 각도 계산.
   - 단어 순서가 바뀌거나 긴 문장 간의 유사도 평가에 강력.
4. **`TypoSuggester` (오타 자동 완성 & 추천기)**:
   - 사전/컬렉션 후보군에서 임계치 이상의 유사도를 만족하는 추천 목록을 순위화하여 반환.
   - 단일 최적 후보(`suggestBest`) 또는 상위 N개 후보(`suggest`) 검색.
5. **`StringSimilarity` 통합 퍼사드**:
   - 단일 진입점에서 모든 알고리즘을 즉시 호출.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.similarity.*;

import java.util.List;
import java.util.Optional;

public class Example {
    public static void main(String[] args) {
        // 1. Levenshtein 편집 거리 & 유사도
        int distance = StringSimilarity.levenshteinDistance("kitten", "sitting"); // 3
        double levScore = StringSimilarity.levenshtein("apple", "appple"); // ~0.833

        // 2. Jaro-Winkler 유사도 (오타/인명 특화)
        double jwScore = StringSimilarity.jaroWinkler("MARTHA", "MARHTA"); // > 0.95

        // 3. N-gram Cosine 유사도
        double cosine = StringSimilarity.cosine("hello world", "world hello"); // > 0.70

        // 4. 오타 자동 교정 및 추천 (Typo Auto-Suggestion)
        List<String> fruits = List.of("apple", "banana", "orange", "pineapple", "grape");
        
        Optional<String> best = StringSimilarity.suggestBest("appple", fruits);
        System.out.println(best.orElse("")); // "apple"

        // 상위 N개 추천 및 점수 확인
        TypoSuggester suggester = new TypoSuggester(TypoSuggester.Algorithm.JARO_WINKLER, 0.6);
        List<TypoSuggester.Suggestion> top = suggester.suggest("app", fruits, 3);
        top.forEach(s -> System.out.println(s.candidate() + ": " + s.similarity()));
    }
}
```
