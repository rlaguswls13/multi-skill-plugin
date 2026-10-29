# 📦 `skill-process-executor`

> **OS 외부 프로세스 및 커맨드 실행 라이브러리 (`ProcessExecutor`, `ProcessResult`)**  
> 실행 타임아웃, 프로세스 강제 종료, 입출력 파이프 데드락 방지 비동기 스트림 드레이닝, 비동기(`CompletableFuture`) 실행 지원

- **아티팩트 좌표**: `com.msp:skill-process-executor:1.0.0`
- **개별 JAR 산출물**: `modules/skill-process-executor/build/libs/skill-process-executor-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리)

---

## 1. 주요 제공 기능 (Features)

1. **빌더 패턴 기반 프로세스 실행**:
   - `command(...)`, `directory(...)`, `environment(...)`, `timeout(...)`, `standardInput(...)` 지원
2. **파이프 데드락 방지 (Deadlock-Free Stream Draining)**:
   - Java Virtual Thread 기반으로 `stdout`과 `stderr`를 동시에 비동기 수신하여 OS 파이프 버퍼 포화로 인한 프로세스 멈춤 현상 원천 차단
3. **타임아웃 및 강제 종료 (Forcible Destruction)**:
   - 설정된 시간 초과 시 자식 프로세스를 `destroyForcibly()`로 안전하게 종료하고 `isTimedOut == true` 리포트
4. **동기 및 비동기 (`CompletableFuture`) 지원**:
   - `execute()` 동기 호출 및 `executeAsync()` 가상 스레드 비동기 호출 모두 지원

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.process.ProcessExecutor;
import com.msp.process.ProcessResult;
import java.time.Duration;

public class ProcessExample {
    public static void main(String[] args) throws Exception {
        // 1. 빌더를 이용한 프로세스 실행 및 타임아웃 지정
        ProcessResult result = ProcessExecutor.builder()
                .command("git", "status", "--porcelain")
                .timeout(Duration.ofSeconds(5))
                .build()
                .execute();

        if (result.isSuccess()) {
            System.out.println("STDOUT: " + result.stdout());
        } else {
            System.err.printf("실행 실패 (exitCode=%d, timedOut=%b): %s%n",
                    result.exitCode(), result.isTimedOut(), result.stderr());
        }

        // 2. 비동기 실행 (CompletableFuture)
        ProcessExecutor.builder()
                .command("java", "-version")
                .build()
                .executeAsync()
                .thenAccept(res -> System.out.println("비동기 완료 시간: " + res.duration()));
    }
}
```
