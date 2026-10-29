# 📦 `skill-api-performance-profiler`

> **순수 Java 21 표준 라이브러리 기반 계층형 호출 트리 API 지연시간 프로파일러 (나노초 정밀도, 백분율 분할, ASCII/Unicode 트리 뷰)**

- **아티팩트 좌표**: `com.msp:skill-api-performance-profiler:1.0.0`
- **개별 JAR 산출물**: `modules/skill-api-performance-profiler/build/libs/skill-api-performance-profiler-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **계층형 호출 트리 추적 (`ProfileScope`, `AutoCloseable`)**:
   - `try-with-resources` 블록을 통한 부모-자식 스텝 자동 중첩 및 스택 관리.
   - 예외 발생 시에도 안전하게 스코프 종료 및 소요 시간 기록.
2. **나노초 정밀도 & 소요 시간 백분율 산출**:
   - `System.nanoTime()` 기반 지연시간 측정.
   - 루트 총 실행 시간 대비 각 단계별 기여 비율(`%`) 자동 계산.
3. **ASCII 및 Unicode 호출 트리 출력**:
   - `printTree()`: UTF-8 박스 드로잉(`├──`, `└──`, `│`) 트리 출력.
   - `printAsciiTree()`: 순수 7비트 ASCII(`+--`, `\--`, `|`) 트리 출력.
4. **ThreadLocal 스레드 컨텍스트 추적 지원**:
   - `Profiler.startTrace(...)`, `Profiler.enter(...)`, `Profiler.stopTrace()` 지원.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.profiler.*;

public class ProfilerExample {
    public static void main(String[] args) {
        // 1. 프로파일러 시작
        Profiler profiler = Profiler.start("UserLoginFlow");

        // 2. 계층형 단계 계측
        try (ProfileScope s1 = profiler.step("validateRequest")) {
            Thread.sleep(5);
        }

        try (ProfileScope s2 = profiler.step("authenticate")) {
            try (ProfileScope s21 = profiler.step("queryUserFromDb")) {
                Thread.sleep(30);
            }
            try (ProfileScope s22 = profiler.step("verifyPasswordHash")) {
                Thread.sleep(15);
            }
        }

        try (ProfileScope s3 = profiler.step("generateJwtToken")) {
            Thread.sleep(10);
        }

        profiler.stop();

        // 3. 호출 트리 출력
        System.out.println(profiler.printTree());
    }
}
```

### 출력 예시 (Output)

```text
[UserLoginFlow] 60.50ms (100.0%)
├── [validateRequest] 5.10ms (8.4%)
├── [authenticate] 45.20ms (74.7%)
│   ├── [queryUserFromDb] 30.10ms (49.8%)
│   └── [verifyPasswordHash] 15.10ms (25.0%)
└── [generateJwtToken] 10.20ms (16.9%)
```
