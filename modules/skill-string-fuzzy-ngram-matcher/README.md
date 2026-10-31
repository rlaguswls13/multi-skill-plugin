# skill-string-fuzzy-ngram-matcher

N-Gram 기반의 Jaccard 및 Sørensen-Dice 문자열 유사도 매칭 및 오타 허용(In-Memory Typo-Tolerant) 검색 인덱서 라이브러리입니다.

## 주요 기능
- **N-Gram 유사도 알고리즘**:
  - **Jaccard 유사도**: $J(A, B) = \frac{|A \cap B|}{|A \cup B|}$
  - **Sørensen-Dice 유사도**: $D(A, B) = \frac{2 \times |A \cap B|}{|A| + |B|}$
- **다국어 및 한글 유니코드 지원**: 영문 대소문자뿐만 아니라 한글 음절/자소 유니코드 N-Gram 완벽 지원
- **역색인(Inverted Index) 기반 Top-K 검색**: 인덱싱된 텍스트 풀에서 유사도 임계치(`minSimilarity`) 이상의 결과를 점수 내림차순 정렬 반환
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리로 가볍고 빠르게 동작

## 사용 예제

```java
import com.msp.fuzzysearch.FuzzyMatchResult;
import com.msp.fuzzysearch.FuzzySearchIndex;
import java.util.List;

public class Example {
    public static void main(String[] args) {
        // Bigram(n=2) Sørensen-Dice 인덱서 생성
        FuzzySearchIndex<String> index = new FuzzySearchIndex<>(2, true);

        index.index("kubernetes-cluster", "ITEM_1");
        index.index("docker-engine", "ITEM_2");
        index.index("helm-charts", "ITEM_3");

        // 오타 포함 검색 ("kubernets")
        List<FuzzyMatchResult<String>> results = index.search("kubernets", 0.5, 5);

        for (FuzzyMatchResult<String> match : results) {
            System.out.printf("Score: %.2f | Text: %s | Payload: %s%n",
                match.score(), match.matchedText(), match.item());
        }
    }
}
```
