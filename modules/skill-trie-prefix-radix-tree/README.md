# 📦 `skill-trie-prefix-radix-tree`

> **순수 Java 21 표준 라이브러리 기반 메모리 압축 기수 트리(Radix / Patricia Trie)**

- **아티팩트 좌표**: `com.msp:skill-trie-prefix-radix-tree:1.0.0`
- **개별 JAR 산출물**: `modules/skill-trie-prefix-radix-tree/build/libs/skill-trie-prefix-radix-tree-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **메모리 압축 엣지 분할 (Edge-Splitting)**:
   - 공통 접두사를 갖는 키 삽입 시 기존 엣지를 공통 부분과 접미사로 자동 분할하여 트리 깊이 및 메모리 최소화
2. **노드 병합 삭제 (Node-Merging)**:
   - 키 삭제 시 자식이 1개뿐인 내부 노드를 자동으로 하위 노드와 병합하여 컴팩트한 기수 트리 상태 유지
3. **초고속 접두사 검색 & 자동완성**:
   - `searchByPrefix(prefix)`: 접두사로 시작하는 모든 값 리스트 반환
   - `entriesWithPrefix(prefix)`: 접두사로 시작하는 모든 (키, 값) 엔트리 반환
4. **최장 접두사 일치 (Longest Prefix Match)**:
   - URL 라우팅 및 IP CIDR / 서브넷 매칭에 최적화된 `longestPrefixOf(key)` 제공
5. **Thread-Safe 동시성 제어**:
   - `ReentrantReadWriteLock` 기반 동시 다중 읽기 및 안전한 쓰기 보장

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.radixtree.RadixTree;
import java.util.List;

public class RadixTreeExample {
    public static void main(String[] args) {
        RadixTree<String> router = new RadixTree<>();

        // 1. 라우트 등록 (자동 엣지 분할)
        router.put("/api/v1/users", "UserController");
        router.put("/api/v1/users/profile", "UserProfileController");
        router.put("/api/v1/orders", "OrderController");
        router.put("/api/v2/items", "ItemV2Controller");

        // 2. 단일 조회
        System.out.println("Users handler: " + router.get("/api/v1/users"));

        // 3. 접두사 검색 (Autocomplete / Prefix Match)
        List<String> v1Handlers = router.searchByPrefix("/api/v1");
        System.out.println("v1 Handlers: " + v1Handlers); // [OrderController, UserController, UserProfileController]

        // 4. 최장 일치 라우팅 (Longest Prefix Of)
        String matchedRoute = router.longestPrefixOf("/api/v1/users/12345/details");
        System.out.println("Matched route: " + matchedRoute); // /api/v1/users

        // 5. 삭제 및 노드 병합
        router.remove("/api/v1/users/profile");
        System.out.println("Remaining size: " + router.size());
    }
}
```
