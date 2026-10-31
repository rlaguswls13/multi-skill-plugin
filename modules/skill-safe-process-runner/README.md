# 📦 `skill-safe-process-runner`

> **순수 Java 21 기반 파이프 버퍼 데드락 & OOM 방지 및 타임아웃 보장 ProcessBuilder 세이프 러너**

- **아티팩트 좌표**: `com.msp:skill-safe-process-runner:1.0.0`
- **개별 JAR 산출물**: `modules/skill-safe-process-runner/build/libs/skill-safe-process-runner-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **파이프 버퍼 데드락(Deadlock) 원천 방지**:
   - Java 21 가상 스레드(Virtual Thread)를 통해 `stdout`과 `stderr` 스트림을 논블로킹으로 즉시 소진(Drain)
   - 자식 프로세스의 출력 버퍼가 차서 OS 레벨에서 영구 블로킹되는 문제 해결
2. **메모리 고갈(OOM) 방지 버퍼 상한 제한**:
   - `maxOutputBytes` (기본 5MB)를 초과하는 대용량 출력 발생 시 메모리 적재를 제한하고 초과 데이터는 폐기하되 스트림 소진은 지속하여 프로세스 정상 종료 유도
3. **타임아웃 보장 및 프로세스 트리(Process Tree) 강제 종료**:
   - 타임아웃 초과 시 부모 프로세스뿐 아니라 Java 9+ `process.descendants()`를 통해 고아 프로세스 및 하위 자식 트리까지 즉시 `destroyForcibly()`

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.processrunner.SafeProcessRunner;
import com.msp.processrunner.ProcessRunResult;
import java.time.Duration;
import java.util.List;

public class ProcessRunnerExample {
    public static void main(String[] args) {
        // 1. 기본 명령어 실행 (5초 타임아웃)
        ProcessRunResult result = SafeProcessRunner.run(
                List.of("git", "status", "--short"),
                Duration.ofSeconds(5)
        );

        if (result.isSuccess()) {
            System.out.println("표준 출력:\n" + result.stdout());
            System.out.println("소요 시간: " + result.executionTime().toMillis() + "ms");
        } else if (result.timedOut()) {
            System.err.println("명령어 실행 시간 초과로 강제 종료되었습니다.");
        } else {
            System.err.println("명령어 실패 (종료 코드: " + result.exitCode() + ")");
            System.err.println("에러 출력:\n" + result.stderr());
        }
    }
}
```
