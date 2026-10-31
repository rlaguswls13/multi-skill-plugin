# 📦 `skill-text-keyword-bm25-scorer`

> **순수 Java 21 표준 라이브러리 기반 Okapi BM25 텍스트 관련성 및 문서 랭킹 검색 엔진**

- **아티팩트 좌표**: `com.msp:skill-text-keyword-bm25-scorer:1.0.0`
- **개별 JAR 산출물**: `modules/skill-text-keyword-bm25-scorer/build/libs/skill-text-keyword-bm25-scorer-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **Okapi BM25 / Lucene 표준 랭킹 알고리즘**:
   - 단어 빈도(TF) 포화 파라미터 `k1` (기본값 1.2)와 문서 길이 정규화 파라미터 `b` (기본값 0.75) 기반 점수 계산
   - 음수 점수 발생을 방지하는 안정적인 IDF 공식 (`ln(1 + (N - df + 0.5) / (df + 0.5))`) 적용
2. **단어 빈도 포화 및 문서 길이 정규화 (Length Normalization)**:
   - 동일 단어의 단순 반복에 의한 점수 폭증 억제 (TF Saturation)
   - 짧은 문서 내에서의 키워드 집중도에 가중치를 부여하고, 긴 문서의 노이즈 불이익을 보정
3. **타입 안전한 제네릭 문서 지원 (`Document<T>`, `ScoredDocument<T>`)**:
   - UUID, String, Long 등 다양한 ID 타입을 수용하는 불변 Java 21 `record` 제공
   - 점수 기준 내림차순 정렬 및 안정적인 순위(Ranking) 출력
4. **제로 런타임 의존성**:
   - 무거운 Lucene, Elasticsearch 없이 순수 Java 21로 인메모리 고속 검색 및 랭킹 지원

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.bm25.Bm25Scorer;
import com.msp.bm25.Document;
import com.msp.bm25.ScoredDocument;

import java.util.List;

public class Bm25Example {
    public static void main(String[] args) {
        // 1. 코퍼스 문서 생성 (ID, 토큰 리스트)
        List<Document<String>> corpus = List.of(
            Document.of("doc-1", "java", "concurrency", "virtual", "threads", "performance"),
            Document.of("doc-2", "python", "data", "science", "machine", "learning"),
            Document.of("doc-3", "java", "spring", "boot", "web", "microservices")
        );

        // 2. BM25 스코어러 생성 (기본 k1=1.2, b=0.75)
        Bm25Scorer scorer = new Bm25Scorer();

        // 3. 쿼리 검색 및 랭킹 계산
        List<String> query = List.of("java", "threads");
        List<ScoredDocument<String>> results = scorer.rank(query, corpus);

        // 4. 결과 출력 (점수 내림차순)
        for (ScoredDocument<String> ranked : results) {
            System.out.printf("Doc ID: %-8s | Score: %.4f%n",
                ranked.document().id(), ranked.score());
        }
    }
}
```
