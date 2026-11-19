# 📦 `skill-cron-dependency-dag-trigger`

> **순수 Java 21 기반 방향성 비순환 그래프(DAG) 태스크 의존성 코디네이터 및 위상 정렬 비동기 파이프라인 트리거**

- **아티팩트 좌표**: `com.msp:skill-cron-dependency-dag-trigger:1.0.0`
- **개별 JAR 산출물**: `modules/skill-cron-dependency-dag-trigger/build/libs/skill-cron-dependency-dag-trigger-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **DAG 태스크 의존성 모델링 (`DagTask`, `DagPipeline`)**:
   - 태스크 노드 간 선행 의존성(`dependencies`)을 정의하여 복잡한 데이터 파이프라인 구성.
   - 선행 작업의 반환값을 후속 작업에서 안전하게 조회할 수 있는 읽기 전용 실행 컨텍스트(`TaskExecutionContext`).
2. **순환 참조 탐지(Cycle Detection) & 위상 정렬(Topological Sort)**:
   - 3색 DFS 기반으로 방향 그래프의 순환 종속성을 사전에 검출하고 경로(`A -> B -> C -> A`)를 명시한 `CyclicDependencyException` 발생.
   - 의존성 선후 관계를 완벽히 보장하는 선형 실행 순서 도출.
3. **비동기 논블로킹 실행 엔진 (`CompletableFuture`)**:
   - 선행 태스크들이 완료되는 즉시 후속 태스크를 병렬/비동기로 기동(`executeAsync()`).
4. **상위 실패 전파 및 선택적 격리 (`UpstreamFailedException`)**:
   - 선행 태스크 실패 시 해당 태스크에 종속된 하위 태스크만 `SKIPPED_UPSTREAM_FAILED` 상태로 안전하게 건너뛰며, 독립적인 다른 브랜치는 정상 수행.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.crondag.*;
import java.util.Set;

public class DagPipelineExample {
    public static void main(String[] args) {
        // 1. 다이아몬드 형태의 DAG 파이프라인 구성 (A -> B, C -> D)
        DagPipeline pipeline = DagPipeline.builder()
                .addTask(DagTask.of("fetchData", ctx -> {
                    System.out.println("Step 1: Fetching source data...");
                    return 100;
                }))
                .addTask(DagTask.of("processTax", Set.of("fetchData"), ctx -> {
                    int data = ctx.getResult("fetchData");
                    System.out.println("Step 2A: Processing tax...");
                    return data * 0.1; // 10.0
                }))
                .addTask(DagTask.of("processDiscount", Set.of("fetchData"), ctx -> {
                    int data = ctx.getResult("fetchData");
                    System.out.println("Step 2B: Processing discount...");
                    return data * 0.05; // 5.0
                }))
                .addTask(DagTask.of("generateSummary", Set.of("processTax", "processDiscount"), ctx -> {
                    double tax = ctx.getResult("processTax");
                    double discount = ctx.getResult("processDiscount");
                    System.out.println("Step 3: Generating final invoice...");
                    return "Invoice Total adjustment: +" + tax + " / -" + discount;
                }))
                .build();

        // 2. 위상 정렬 순서 확인
        System.out.println("Topological Execution Order: " + pipeline.topologicalSort());

        // 3. 파이프라인 실행
        DagExecutionReport report = pipeline.execute();

        if (report.isAllSuccess()) {
            System.out.println("Pipeline Finished Successfully!");
            System.out.println("Result: " + report.get("generateSummary").value());
        } else {
            System.err.println("Pipeline encountered failures or skipped tasks.");
        }
    }
}
```
