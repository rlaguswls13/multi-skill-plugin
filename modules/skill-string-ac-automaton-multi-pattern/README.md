# 📦 `skill-string-ac-automaton-multi-pattern`

> **Aho-Corasick 알고리즘 기반 고성능 다중 패턴 트라이(Trie) 문자열 고속 검색 오토마톤**

- **아티팩트 좌표**: `com.msp:skill-string-ac-automaton-multi-pattern:1.0.0`
- **개별 JAR 산출물**: `modules/skill-string-ac-automaton-multi-pattern/build/libs/skill-string-ac-automaton-multi-pattern-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **최적의 다중 패턴 검색 시간 복잡도 $O(N + M)$**:
   - $K$개의 키워드를 한 번의 텍스트 스캔(단일 패스)으로 동시에 탐색
   - BFS 기반 Failure 링크 및 출력 사전 접미사(Output Link) 자동 구축
2. **중첩(Overlapping) 및 비중첩(Non-Overlapping) 매칭**:
   - 모든 중첩 키워드 매칭 위치 검출 (`findAll`)
   - 최장 일치 우선(Longest-Match First) 비중첩 필터링 (`findNonOverlapping`)
3. **대소문자 무구분(Case-Insensitive) 지원**:
   - 원본 인덱스는 정확히 유지하면서 대소문자 구분 없는 고속 검색 지원
4. **마스킹 및 치환 유틸리티**:
   - 금칙어/민감정보 마스킹 (`maskMatches`)
   - 람다식 기반 매칭 키워드 치환 (`replaceMatches`)
5. **사용자 정의 페이로드(Payload) 연동**:
   - 키워드 등록 시 메타데이터, 카테고리, ID 객체 등을 함께 바인딩하여 매칭 결과와 함께 수신

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.ahocorasick.AhoCorasick;
import com.msp.ahocorasick.PatternMatch;

import java.util.List;

public class AhoCorasickExample {
    public static void main(String[] args) {
        // 1. 오토마톤 빌드
        AhoCorasick ac = AhoCorasick.builder()
                .addKeyword("he")
                .addKeyword("she")
                .addKeyword("his")
                .addKeyword("hers")
                .caseInsensitive(true)
                .build();

        // 2. 전체 매칭 탐색 (중첩 포함)
        List<PatternMatch> matches = ac.findAll("UsHeRs");
        for (PatternMatch m : matches) {
            System.out.printf("발견: %s [%d..%d]\n", m.keyword(), m.start(), m.end());
        }
        // 발견: she [1..4]
        // 발견: he [2..4]
        // 발견: hers [2..6]

        // 3. 민감 키워드 마스킹
        String masked = ac.maskMatches("ushers", '*');
        System.out.println("마스킹 결과: " + masked);
        // u*****

        // 4. 비중첩 치환
        String replaced = ac.replaceMatches("ushers", m -> "{" + m.keyword() + "}");
        System.out.println("치환 결과: " + replaced);
    }
}
```
