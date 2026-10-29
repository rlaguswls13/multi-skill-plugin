# 📦 `skill-diff-patcher`

> **외부 라이브러리 없이 순수 Java 21 표준 라이브러리로 중첩 Map, List, Java Record 간의 재귀적 차이점 검출(`DiffResult`), 사람 친화적인 감사 로그 포맷팅, RFC 6902 JSON 패치 생성, 그리고 변경 사항 적용(`apply`) 및 롤백(`rollback`)을 지원하는 독립 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-diff-patcher:1.0.0`
- **개별 JAR 산출물**: `modules/skill-diff-patcher/build/libs/skill-diff-patcher-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **`DiffDetector`**:
   - 중첩 Map, List, Java 21 `record` 간의 심층 재귀 비교
   - 변경 유형(`ChangeType`): `ADDED`, `REMOVED`, `MODIFIED`
   - 점/대괄호 표기법 경로: `address.city`, `items[0].price`
   - `oldValue`와 `newValue`를 모두 보존하는 불변 `FieldChange` 레코드
2. **`AuditPatcher`**:
   - 감사 로그 포맷팅: `[address.city] 'Seoul' -> 'Busan'`
   - RFC 6902 규격 JSON Patch 연산 구조 변환 (`add`, `remove`, `replace`)
   - 전방향 패치 적용: `apply(targetMap, diffResult)`
   - 역방향 롤백 복구: `rollback(targetMap, diffResult)`
3. **`DiffPatcher`**:
   - 단일 정적 메서드로 비교, 감사 로그, 패치, 롤백을 총괄하는 통합 퍼사드(Facade)

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.diff.*;
import java.util.*;

// 1. 원본 상태와 변경 상태 비교
Map<String, Object> oldState = new LinkedHashMap<>();
oldState.put("name", "Alice");
oldState.put("address", Map.of("city", "Seoul"));
oldState.put("hobbies", List.of("Reading"));

Map<String, Object> newState = new LinkedHashMap<>();
newState.put("name", "Alice");
newState.put("address", Map.of("city", "Busan")); // MODIFIED
newState.put("hobbies", List.of("Reading", "Gaming")); // ADDED at [1]
newState.put("vip", true); // ADDED

DiffResult diff = DiffPatcher.diff(oldState, newState);

// 2. 감사 로그 출력
diff.toAuditLogs().forEach(System.out::println);
// [address.city] 'Seoul' -> 'Busan'
// [hobbies[1]] ADDED: 'Gaming'
// [vip] ADDED: 'true'

// 3. RFC 6902 JSON Patch 변환
List<Map<String, Object>> patchOps = DiffPatcher.toJsonPatch(diff);

// 4. 패치 적용 및 롤백
DiffPatcher.apply(oldState, diff);
// oldState가 newState의 값들로 갱신됨

DiffPatcher.rollback(oldState, diff);
// oldState가 초기 상태로 완벽히 원복됨
```
