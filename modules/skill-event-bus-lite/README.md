# 📡 `skill-event-bus-lite`

> **외부 프레임워크 의존성 없이 타입 안전(Typed) 구독, 동기/비동기 발행, 구독자 예외 격리(Fault Tolerance), 구독 해제 및 데드 레터(Dead Letter) 처리를 지원하는 인메모리 이벤트 버스 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-event-bus-lite:1.0.0`
- **개별 JAR 산출물**: `modules/skill-event-bus-lite/build/libs/skill-event-bus-lite-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **타입 안전 구독 (`subscribe(Class<E>, Consumer<E>)`)**:
   - 컴파일 타임 타입 검사 지원 및 캐스팅 없는 람다 리스너 등록.
2. **동기 & 비동기 발행 (`publish`, `publishAsync`)**:
   - 즉시 동기 처리 또는 Java 21 가상 스레드 기반 `CompletableFuture<Void>` 비동기 발행.
3. **구독자 예외 격리 (Exception Isolation)**:
   - 특정 구독자에서 예외가 발생하더라도 다른 구독자의 실행이 중단되지 않고 `SubscriberExceptionHandler`로 안전하게 위임.
4. **다형성 이벤트 라우팅 (Polymorphic Routing)**:
   - 상위 인터페이스 또는 상위 클래스 구독 시 하위 타입 이벤트 자동 매칭 및 수신.
5. **구독 해제 & AutoCloseable (`Subscription`)**:
   - `subscription.unsubscribe()` 또는 `try-with-resources` 구문을 통한 자동 해제 지원.
6. **Dead Letter 처리 (`DeadLetterHandler`, `DeadLetterEvent`)**:
   - 등록된 구독자가 없는 미처리 이벤트를 탐지하고 데드 레터 핸들러로 라우팅.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.eventbus.*;

// 1. 이벤트 레코드 정의
record OrderPlaced(String orderId, long amount) {}

// 2. 이벤트 버스 생성
EventBus eventBus = EventBus.builder()
    .exceptionHandler((ex, ctx) -> {
        System.err.println("구독자 예외 발생 격리: " + ex.getMessage());
    })
    .deadLetterHandler(dl -> {
        System.err.println("미처리 이벤트 발생: " + dl.event());
    })
    .build();

// 3. 타입 안전 구독 (Subscription 반환)
Subscription sub = eventBus.subscribe(OrderPlaced.class, event -> {
    System.out.printf("주문 접수 수신: ID=%s, 금액=%d원%n", event.orderId(), event.amount());
});

// 4. 동기 발행
eventBus.publish(new OrderPlaced("ORD-1001", 35000));

// 5. 비동기 발행 (Virtual Threads / CompletableFuture)
eventBus.publishAsync(new OrderPlaced("ORD-1002", 50000))
    .thenRun(() -> System.out.println("비동기 이벤트 발행 완료"));

// 6. 구독 해제
sub.unsubscribe();
```
