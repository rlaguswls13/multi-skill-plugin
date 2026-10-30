# Tree Structure Builder (`skill-tree-structure-builder`)

데이터베이스 테이블의 `id`, `parent_id` 평면 행(Flat Records)을 중첩 계층형 트리(Nested Tree) 구조로 조립하고, DFS/BFS 순회, 선형화(Flattening), 순환 참조(Cycle Loop) 및 고아 노드(Orphan Node) 탐지를 지원하는 순수 Java 21 유틸리티입니다.

외부 라이브러리 없이 카테고리, 조직도, 메뉴 계층, 댓글 대댓글, 파일 디렉터리 트리 등을 완벽하게 모델링할 수 있습니다.

## Features

- **불변 트리 노드 레코드**: `TreeNode<T>(String id, String parentId, T data, List<TreeNode<T>> children)`
- **평면 리스트 -> 계층 트리 변환**: `id`와 `parentId` 기반 다중 루트(Multi-roots) 트리 자동 조립
- **임의 도메인 엔티티 직접 변환**: `Function` 추출자 전달을 통한 즉각적인 DTO/Entity 변환
- **트리 순회 (Traversal)**:
  - 깊이 우선 탐색 (`depthFirstSearch`): 전위 순회(Pre-order DFS)
  - 너비 우선 탐색 (`breadthFirstSearch`): 레벨 순회(Level-order BFS)
- **트리 선형화 (Flatten)**: 중첩 트리를 순회 순서에 맞춰 1차원 리스트로 복원
- **순환 참조(Cycle) 안전 탐지**: `A -> B -> A`, 자기 참조 등 무한 루프를 유발하는 순환 관계 사전 차단 (`IllegalStateException`)
- **고아 노드(Orphan) 탐지 및 구제**: 부모 ID가 존재하지 않는 노드를 별도 분리(`TreeResult`)하거나 루트로 승격 처리
- **Zero Runtime Dependencies**: 순수 Java 21 표준 라이브러리만 사용

## Usage

```java
import com.msp.tree.TreeBuilder;
import com.msp.tree.TreeNode;
import java.util.List;

// 1. 데이터베이스 등에서 조회한 평면 노드 목록
List<TreeNode<String>> flatNodes = List.of(
    new TreeNode<>("electronics", null, "전자제품"),
    new TreeNode<>("laptops", "electronics", "노트북"),
    new TreeNode<>("gaming-laptops", "laptops", "게이밍 노트북"),
    new TreeNode<>("smartphones", "electronics", "스마트폰"),
    new TreeNode<>("books", null, "도서")
);

// 2. 중첩 계층 트리 생성
List<TreeNode<String>> roots = TreeBuilder.buildTree(flatNodes);

// 3. 깊이 우선 탐색 (DFS)
TreeBuilder.depthFirstSearch(roots, node -> {
    System.out.println("Visited: " + node.id() + " (" + node.data() + ")");
});

// 4. 너비 우선 탐색 (BFS)
TreeBuilder.breadthFirstSearch(roots, node -> {
    System.out.println("Level-order: " + node.id());
});

// 5. 트리 선형 평탄화 (Flatten)
List<TreeNode<String>> flattened = TreeBuilder.flatten(roots);
```
