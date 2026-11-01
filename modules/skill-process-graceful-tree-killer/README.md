# 📦 `skill-process-graceful-tree-killer`

> **순수 Java 9+ ProcessHandle 기반 OS 프로세스 트리 순차 우아한 종료(Graceful Shutdown) 및 강제 회수기**

- **아티팩트 좌표**: `com.msp:skill-process-graceful-tree-killer:1.0.0`
- **개별 JAR 산출물**: `modules/skill-process-graceful-tree-killer/build/libs/skill-process-graceful-tree-killer-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **상향식(Bottom-Up) 3단계 프로세스 트리 종료 프로토콜**:
   - **1단계 (Graceful SIGTERM)**: 트리 내 가장 깊은 자식 프로세스(Deepest Descendants)부터 최상위 부모 순서로 `destroy()` 신호 순차 전달.
   - **2단계 (유예 대기 - Await Grace Period)**: 비동기 `onExit()` 퓨처를 통해 설정된 시간(기본 5초) 동안 자연스러운 리소스 정리 및 종료 대기.
   - **3단계 (강제 회수 - Forced SIGKILL)**: 유예 시간 경과 후에도 종료되지 않은 잔여 프로세스에 `destroyForcibly()`를 발행하여 좀비 프로세스 방지.
2. **프로세스 계층 탐색 & Dry Run**:
   - `inspectTree(pid)`: 실제 신호를 보내지 않고 대상 프로세스와 전체 하위 자식 목록을 안전하게 사전 조회.
3. **SafeMode 안전장치**:
   - 커널/시스템 필수 PID(PID 0, PID 1) 및 실행 중인 자기 자신(현재 JVM 프로세스)을 오인하여 종료하지 않도록 자동 차단.
4. **상세 종료 리포트 (`ProcessKillReport`)**:
   - 우아하게 종료된 프로세스, 강제 회수된 프로세스, 실패 프로세스 목록 및 총 소요 시간 상세 제공.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.prockill.ProcessKillReport;
import com.msp.prockill.ProcessTreeKiller;
import java.time.Duration;

public class ProcessTreeKillerExample {
    public static void main(String[] args) {
        long targetPid = 12345L;

        // 1. 단일 메서드로 안전한 트리 종료
        ProcessKillReport report = ProcessTreeKiller.kill(targetPid, Duration.ofSeconds(3));

        System.out.println("Target PID: " + report.targetPid());
        System.out.println("Terminated Gracefully: " + report.terminatedGracefully().size());
        System.out.println("Killed Forcibly: " + report.killedForcibly().size());
        System.out.println("Failed: " + report.failed().size());
        System.out.println("Total Time: " + report.totalDuration().toMillis() + "ms");

        // 2. Dry Run 모드로 트리만 검사
        ProcessKillReport dryReport = ProcessTreeKiller.create()
                .dryRun(true)
                .execute(targetPid);
        dryReport.terminatedGracefully().forEach(p -> 
            System.out.println("Found process: " + p.pid() + " (" + p.command() + ")")
        );
    }
}
```
