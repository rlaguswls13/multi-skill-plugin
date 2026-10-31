# 📦 `skill-event-deduplicator-window`

> **순수 Java 21 기반 슬라이딩 시간 윈도우 및 TTL 만료 관리자 (Kafka/RabbitMQ 메시지 중복 처리 방지)**

- **아티팩트 좌표**: `com.msp:skill-event-deduplicator-window:1.0.0`
- **개별 JAR 산출물**: `modules/skill-event-deduplicator-window/build/libs/skill-event-deduplicator-window-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **원자적 슬라이딩 윈도우 중복 검출**:
   - `isDuplicate(key)`: 최초 수신 시 등록 후 `false`, 윈도우 시간 내 재수신 시 `true`를 즉시 반환
   - 멀티스레드 환경에서도 정확히 단 1개 스레드만 최초 등록 성공을 보장
2. **조회 전용 메서드 (`checkOnly`)**:
   - 상태 변경 없이 윈도우 내 키 잔존 여부 확인
3. **용량 상한 보호 및 TTL 자동 만료**:
   - `maxCapacity` 초과 시 만료된 키 자동 정리 및 FIFO 축출
   - `cleanUp()`을 통한 수동 정리 및 주기적 백그라운드 스케줄러 지원
4. **결정론적 테스트 지원**:
   - 가상 `Clock` 주입을 통해 `Thread.sleep` 없이 정밀한 TTL 만료 단위 테스트 가능

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.dedupwindow.EventDeduplicator;
import java.time.Duration;

public class DedupExample {
    public static void main(String[] args) {
        // 5분 윈도우, 최대 10,000건 유지
        try (var dedup = new EventDeduplicator<String>(Duration.ofMinutes(5), 10_000)) {
            String messageId = "order-evt-98234";

            // 1. 첫 번째 메시지 도착
            if (dedup.isDuplicate(messageId)) {
                System.out.println("중복 메시지 건너뜀!");
            } else {
                System.out.println("주문 이벤트 정상 처리: " + messageId);
            }

            // 2. 네트워크 재전송으로 동일 메시지 재수신
            if (dedup.isDuplicate(messageId)) {
                System.out.println("중복 감지: 이미 처리된 메시지입니다.");
            }

            // 3. 현재 윈도우 내 활성 키 개수 확인
            System.out.println("현재 활성 키 개수: " + dedup.size());
        }
    }
}
```
