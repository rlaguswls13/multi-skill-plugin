# 📦 `skill-sketch-minhash-lsh-lite`

> **대규모 텍스트/문서 자카드 유사도 추정 및 후보군 탐색을 위한 MinHash & LSH (Locality-Sensitive Hashing) 인덱서**

- **아티팩트 좌표**: `com.msp:skill-sketch-minhash-lsh-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-sketch-minhash-lsh-lite/build/libs/skill-sketch-minhash-lsh-lite-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **MinHash 시그니처 생성**:
   - $K$개의 독립 해시 순열(기본 128개)을 이용하여 임의의 토큰/슁글 집합을 $K$차원 최소 해시 벡터로 압축
   - 브로더의 정리(Broder's Theorem)에 따라 두 시그니처의 일치 비율이 자카드 유사도(Jaccard Similarity)에 수렴
2. **LSH (Locality-Sensitive Hashing) 인덱싱**:
   - 밴드-행 분할 기법($B$ Bands $\times R$ Rows $= K$) 적용
   - 전체 문서를 일일이 비교하지 않고(Sub-linear $O(1)$) 유사 후보군(Candidate Pairs)을 즉각 추출
3. **슁글 생성 유틸리티 (`ShingleGenerator`)**:
   - 문자 $k$-슁글 (`characterShingles`) 및 단어 $n$-그램 (`wordNGrams`) 지원
4. **동적 인덱싱 & 스레드 안전성**:
   - `ConcurrentHashMap` 기반 스레드 안전한 인덱스 추가/삭제/조회
   - 임계치($\theta$) 기반 필터링 및 유사도 점수 정렬 반환 (`querySimilarWithScores`)

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.minhash.LshIndex;
import com.msp.minhash.ShingleGenerator;
import com.msp.minhash.SimilarityResult;
import java.util.List;
import java.util.Set;

public class MinHashExample {
    public static void main(String[] args) {
        // 1. LSH 인덱스 초기화 (기본 128 해시, 16 밴드, 8 행)
        LshIndex lsh = new LshIndex();

        // 2. 문서 슁글화 및 색인
        String doc1 = "Artificial intelligence and machine learning neural networks";
        String doc2 = "Machine learning and artificial intelligence deep neural networks";
        String doc3 = "Delicious artisan bakery sourdough bread recipe";

        lsh.index("doc-ai-1", ShingleGenerator.characterShingles(doc1, 3));
        lsh.index("doc-ai-2", ShingleGenerator.characterShingles(doc2, 3));
        lsh.index("doc-recipe", ShingleGenerator.characterShingles(doc3, 3));

        // 3. 유사 문서 검색 (유사도 임계치 0.6 이상)
        Set<String> queryTokens = ShingleGenerator.characterShingles(
                "Artificial intelligence machine learning deep models", 3);

        List<SimilarityResult> results = lsh.querySimilarWithScores(queryTokens, 0.60);
        for (SimilarityResult res : results) {
            System.out.printf("Found: %s (Similarity: %.2f%%)%n", res.docId(), res.similarity() * 100);
        }
    }
}
```
