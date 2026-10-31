# 📦 `skill-diff-text-myers-lcs`

> **순수 Java 21 표준 라이브러리 기반 Eugene Myers O(ND) 최단 편집 스크립트(SES) 및 LCS 텍스트 차이(Diff) / Unified Diff 엔진**

- **아티팩트 좌표**: `com.msp:skill-diff-text-myers-lcs:1.0.0`
- **개별 JAR 산출물**: `modules/skill-diff-text-myers-lcs/build/libs/skill-diff-text-myers-lcs-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **Eugene Myers O(ND) 최단 편집 스크립트(SES) 알고리즘**:
   - Git, GNU Diff의 기반이 되는 O(ND) Myers 알고리즘 구현
   - 원본과 수정본 사이의 최소 변경 단계(KEEP, INSERT, DELETE) 및 Longest Common Subsequence (LCS) 정밀 계산
2. **1-Based 라인 넘버링 구조체 (`DiffItem`)**:
   - 변경 항목마다 `oldLineNum`, `newLineNum`을 보존하여 IDE 플러그인, 코드 리뷰 도구, VCS 뷰어에 즉시 연동 가능
3. **표준 Unified Diff 포맷 생성 (`toUnifiedDiff`)**:
   - `--- a\n+++ b\n@@ -start,count +start,count @@` 표준 헝크(Hunk) 헤더 및 컨텍스트 라인 그룹화 지원
   - 인접 변경 영역 자동 병합 및 동일 텍스트 감지 시 빈 문자열 반환
4. **제로 런타임 의존성**:
   - java-diff-utils 등의 외부 의존성 없이 순수 Java 21 표준 라이브러리만으로 동작

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.myersdiff.DiffItem;
import com.msp.myersdiff.MyersDiff;

import java.util.List;

public class MyersDiffExample {
    public static void main(String[] args) {
        List<String> original = List.of(
            "public class Calculator {",
            "    public int add(int a, int b) {",
            "        return a - b;", // 버그 라인
            "    }",
            "}"
        );

        List<String> revised = List.of(
            "public class Calculator {",
            "    public int add(int a, int b) {",
            "        return a + b;", // 수정 라인
            "    }",
            "    public int sub(int a, int b) { return a - b; }", // 신규 라인
            "}"
        );

        // 1. 세부 Diff 항목 계산
        List<DiffItem> diffItems = MyersDiff.computeDiff(original, revised);
        for (DiffItem item : diffItems) {
            System.out.printf("[%s] L%d -> R%d: %s%n",
                item.type(), item.oldLineNum(), item.newLineNum(), item.text());
        }

        // 2. 표준 Unified Diff 생성 (컨텍스트 라인 3줄)
        String unifiedDiff = MyersDiff.toUnifiedDiff("Calculator.java", "Calculator.java", original, revised, 3);
        System.out.println("\n=== Unified Diff ===");
        System.out.println(unifiedDiff);
    }
}
```
