# 📦 `skill-sketch-simhash-near-duplicate`

> **순수 Java 21 기반 64비트 SimHash 지역 민감 해싱(LSH) 및 $O(1)$ 블록 분할 역인덱스(`SimHashTable`) 유사 문서/텍스트 탐지기**

- **아티팩트 좌표**: `com.msp:skill-sketch-simhash-near-duplicate:1.0.0`
- **개별 JAR 산출물**: `modules/skill-sketch-simhash-near-duplicate/build/libs/skill-sketch-simhash-near-duplicate-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **64비트 SimHash 핑거프린트 생성 (`SimHash`)**:
   - 텍스트 토큰 및 가중치 빈도(Frequency)를 바탕으로 비트 가중 투표를 수행하여 64비트 정수 핑거프린트 도출.
   - 단어 단위 분할(`Tokenizer.WORDS`), 문자 N-Gram(`charNGrams(n)`), 단어 N-Gram(`wordNGrams(n)`) 지원.
2. **해밍 거리(Hamming Distance) 및 유사도 측정**:
   - `Long.bitCount(hash1 ^ hash2)`를 통한 초고속 비트 차이 계산.
   - `isNearDuplicate(doc1, doc2, maxDistance)` 임계값 검증 (웹 문서 기준 보통 해밍 거리 3 이하).
3. **서브 리니어(Sub-linear) 후보 검색 인덱스 (`SimHashTable`)**:
   - 64비트 해시를 비둘기집 원리(Pigeonhole Principle)에 기초하여 $B$개(기본 4개, 각 16비트) 블록으로 분할 인덱싱.
   - 수백만 개의 문서 중 해밍 거리 $k \le 3$ 이내의 유사 후보를 $O(1)$ 수준으로 초고속 필터링 후 정확한 거리 판정.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.simhash.*;
import java.util.List;

public class SimHashExample {
    public static void main(String[] args) {
        String doc1 = "Java 21 introduces virtual threads and pattern matching for high throughput.";
        String doc2 = "Java 21 introduces virtual threads and pattern matching for high performance.";

        // 1. 64비트 핑거프린트 계산
        long hash1 = SimHash.compute(doc1);
        long hash2 = SimHash.compute(doc2);

        int distance = SimHash.hammingDistance(hash1, hash2);
        System.out.printf("Hamming Distance: %d (Similarity: %.2f%%)%n",
                distance, SimHash.similarity(hash1, hash2) * 100);

        // 2. 인덱스 테이블 활용한 근사 중복 문서 검색
        SimHashTable<String> index = new SimHashTable<>(4);
        index.put(doc1, "Article-101");
        index.put("Completely unrelated text about baking cookies.", "Article-102");

        List<SimHashTable.SearchResult<String>> matches =
                index.findNearDuplicates(doc2, 3);

        for (SimHashTable.SearchResult<String> match : matches) {
            System.out.printf("Found Near-Duplicate: %s (Distance: %d)%n",
                    match.value(), match.distance());
        }
    }
}
```
