# 📦 `skill-string-levenshtein-damerau`

> **순수 Java 21 기반의 인접 문자 전치(Transposition: "ab" <-> "ba") 지원 Damerau-Levenshtein 편집 거리 및 유사도 매칭기**

- **아티팩트 좌표**: `com.msp:skill-string-levenshtein-damerau:1.0.0`
- **개별 JAR 산출물**: `modules/skill-string-levenshtein-damerau/build/libs/skill-string-levenshtein-damerau-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **4대 편집 연산 단일 비용(Cost=1) 처리**:
   - 삽입(Insertion), 삭제(Deletion), 치환(Substitution) 외에
   - 키보드 오타의 대표 원인인 **인접 문자 전치(Transposition: e.g. "tehs" <-> "thes")**를 비용 1로 정밀 계산
2. **정규화된 유사도 점수 계산**:
   - `similarity(s1, s2)`: 0.0 (완전 상이) ~ 1.0 (완전 일치) 범위의 유사도 지수 반환
3. **임계값 기반 고속 가지치기 (Branch Pruning)**:
   - `isWithinDistance(s1, s2, maxDistance)`: 길이 차이 $O(1)$ 검증 및 행 최소값 초과 시 조기 종료를 통한 대량 사전 매칭 고속화

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.damerau.DamerauLevenshtein;

public class DamerauExample {
    public static void main(String[] args) {
        String input = "receive";
        String candidate = "recieve"; // 'ei' <-> 'ie' 인접 전치 오타

        // 1. 편집 거리 계산 (전치 오타이므로 거리 1)
        int dist = DamerauLevenshtein.distance(input, candidate);
        System.out.println("편집 거리: " + dist); // 1

        // 2. 문자열 유사도 백분율
        double sim = DamerauLevenshtein.similarity(input, candidate);
        System.out.printf("유사도: %.2f%%\n", sim * 100); // 85.71%

        // 3. 임계치 이내 빠른 검색 (사전 검색 최적화)
        boolean match = DamerauLevenshtein.isWithinDistance(input, candidate, 2);
        System.out.println("허용 오타 범위 내 여부: " + match); // true
    }
}
```
