# 📦 `skill-text-inverted-index-lite`

> **순수 Java 21 표준 라이브러리 기반 인메모리 역색인(Inverted Index) 전문 검색 엔진 (다중 필드 인덱싱, 불리언 쿼리 AST 파서, BM25 가중치 랭킹)**

- **아티팩트 좌표**: `com.msp:skill-text-inverted-index-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-text-inverted-index-lite/build/libs/skill-text-inverted-index-lite-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **인메모리 역색인 엔진 (`InvertedIndex<T>`)**:
   - `addDocument(String docId, T data, Map<String, String> textFields)`: 제목, 본문, 태그 등 다중 텍스트 필드 색인.
   - `setFieldBoost(String field, double boost)`: 필드별 가중치 배율 설정 (예: `title` 2.0배, `body` 1.0배).
   - 동적 문서 추가 및 안전한 삭제(`removeDocument`), 스레드 안전성(`ReentrantReadWriteLock`).
2. **토큰화 및 전처리 (`Tokenizer`)**:
   - 영문/유니코드 단어 정규화 (소문자 변환, 문장 부호 제거).
   - 기본 불용어(Stopwords) 필터링 및 사용자 정의 토크나이저 지원.
3. **불리언 쿼리 파서 (`BooleanQueryParser`)**:
   - 연산자 우선순위: `NOT` > `AND` > `OR`.
   - 괄호 중첩 지원: `java AND (resilience OR concurrency) AND NOT deprecated`.
   - 인접 단어 간 암시적 AND 연산 지원 (`distributed database`).
4. **BM25 연관도 채점 및 Top-K 랭킹**:
   - Okapi BM25 기반 정밀 relevance scoring ($k_1 = 1.2$, $b = 0.75$, 문서 길이 정규화).
   - Top-K 스코어 내림차순 정렬 및 매칭된 단어 빈도 반환 (`SearchResult<T>`).

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.invindex.*;
import java.util.Map;
import java.util.List;

public class Example {
    public static void main(String[] args) {
        // 1. 역색인 인스턴스 생성 및 필드 가중치 설정
        InvertedIndex<String> index = new InvertedIndex<>();
        index.setFieldBoost("title", 2.0);
        index.setFieldBoost("body", 1.0);

        // 2. 문서 색인
        index.addDocument("doc1", "Doc 1 Payload", Map.of(
                "title", "Java Concurrency & Virtual Threads",
                "body", "Exploring Project Loom virtual threads and concurrency models."
        ));
        index.addDocument("doc2", "Doc 2 Payload", Map.of(
                "title", "Resilience Patterns in Distributed Systems",
                "body", "Circuit breaker, rate limiting, and fault tolerance in Java microservices."
        ));

        // 3. 불리언 쿼리 검색 및 상위 K개 결과 추출
        List<SearchResult<String>> results = index.search(
                "java AND (resilience OR concurrency) AND NOT deprecated",
                5
        );

        for (SearchResult<String> r : results) {
            System.out.printf("Doc ID: %s, Score: %.4f, Data: %s, Matched: %s%n",
                    r.docId(), r.score(), r.data(), r.matchedTerms());
        }
    }
}
```
