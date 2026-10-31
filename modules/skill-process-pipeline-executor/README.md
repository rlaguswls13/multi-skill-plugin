# 📦 `skill-process-pipeline-executor`

> **순수 Java 21 표준 라이브러리 기반 데드락 방지 비동기 다중 프로세스 파이프라인 (Stdout -> Stdin) 실행 엔진**

- **아티팩트 좌표**: `com.msp:skill-process-pipeline-executor:1.0.0`
- **개별 JAR 산출물**: `modules/skill-process-pipeline-executor/build/libs/skill-process-pipeline-executor-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **데드락 방지 다단계 프로세스 파이프라인 (Multi-Stage Process Pipeline)**:
   - Java 9+ `ProcessBuilder.startPipeline`을 활용하여 선행 프로세스의 stdout을 후행 프로세스의 stdin으로 직결
   - Java 21 가상 스레드(Virtual Threads)를 통해 모든 파이프라인 단계의 stderr를 비동기 실시간 드레인하여 OS 파이프 버퍼 충만으로 인한 데드락 완전 방지
2. **타임아웃 보장 및 프로세스 트리 강제 회수 (Timeout & Process Tree Cleanup)**:
   - 설정된 타임아웃 초과 시 자식/후손 프로세스를 포함한 전체 프로세스 트리(`ProcessHandle.descendants()`)를 안전하고 완벽하게 강제 종료
3. **유연한 스테이지 및 환경 변수 구성**:
   - `PipelineStage` 레코드 및 파이프라인 전역/스테이지별 작업 디렉터리(`workingDir`), 환경 변수(`environment`) 설정 지원
   - 크로스 플랫폼(Windows, macOS, Linux) 완벽 호환
4. **제로 런타임 의존성**:
   - Apache Commons Exec, zt-exec 등 무거운 서드파티 라이브러리 없이 순수 Java 21 표준 라이브러리만으로 동작

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.processpipe.PipelineResult;
import com.msp.processpipe.ProcessPipeline;

import java.time.Duration;
import java.util.List;

public class PipelineExample {
    public static void main(String[] args) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        // 1. 2단계 파이프라인 구성 (Producer -> Filter)
        ProcessPipeline pipeline = ProcessPipeline.create();

        if (isWindows) {
            pipeline.stage("cmd.exe", "/c", "echo line1& echo matchedTarget& echo line3");
            pipeline.stage("findstr", "matchedTarget");
        } else {
            pipeline.stage("sh", "-c", "printf 'line1\\nmatchedTarget\\nline3\\n'");
            pipeline.stage("grep", "matchedTarget");
        }

        // 2. 최대 5초 타임아웃으로 파이프라인 실행
        PipelineResult result = pipeline.execute(Duration.ofSeconds(5));

        // 3. 실행 결과 확인
        System.out.println("성공 여부: " + result.isSuccess());
        System.out.println("종료 코드: " + result.exitCode());
        System.out.println("소요 시간: " + result.duration().toMillis() + "ms");
        System.out.println("파이프라인 출력:\n" + result.stdout());
    }
}
```
