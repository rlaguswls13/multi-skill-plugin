# ⚡ skill-concurrent-event-accumulator

수량 임계치(N건) 또는 시간 윈도우(T초) 기반의 동시성 안전(Thread-Safe) 이벤트 배치 버퍼 어큐뮬레이터 라이브러리입니다.

## ✨ 주요 기능
- **수량 임계치 즉시 플러시**: 버퍼가 지정된 `maxBatchSize`에 도달하면 즉각 백그라운드 소비자로 일괄 전송
- **시간 윈도우 주기적 자동 플러시**: 지정된 시간(`maxFlushInterval`) 동안 버퍼가 차지 않더라도 주기적으로 잔여 이벤트 자동 방출
- **Non-blocking Drain 아키텍처**: 락 내부에서는 리스트 레퍼런스 스왑(drain)만 가볍게 수행하여 대기 시간을 최소화
- **정교한 리소스 수명주기**: `AutoCloseable` 구현으로 `close()` 시 대기 중인 잔여 이벤트를 최종 플러시하고 스케줄러 안전 종료
- **Pure Java 21**: 외부 런타임 3rd-party 의존성 Zero

---

## 🚀 빠른 시작 (Quick Start)

### 1. 기본 사용법 (try-with-resources)
```java
import com.msp.accumulator.EventAccumulator;
import java.time.Duration;
import java.util.List;

public class Example {
    public static void main(String[] args) {
        // 50건 모이거나 100ms 경과 시 배치 flush
        try (EventAccumulator<String> accumulator = new EventAccumulator<>(
                50,
                Duration.ofMillis(100),
                batch -> sendBulkToElasticsearch(batch)
        )) {
            for (int i = 0; i < 120; i++) {
                accumulator.add("event-" + i);
            }
        } // try 블록 종료 시 잔여 20건 자동 flush 및 리소스 정리
    }

    private static void sendBulkToElasticsearch(List<String> events) {
        System.out.printf("Flushing %d events: %s\n", events.size(), events);
    }
}
```
