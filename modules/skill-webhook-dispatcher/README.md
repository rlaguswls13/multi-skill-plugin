# 🪝 `skill-webhook-dispatcher`

> **HMAC-SHA256 디지털 서명, 지수 백오프 자동 재시도, 전송 라이프사이클 리스너, 그리고 Dead-Letter Queue(DLQ)를 제공하는 비동기 웹훅 디스패처 단일 JAR 모듈**

- **아티팩트 좌표**: `com.msp:skill-webhook-dispatcher:1.0.0`
- **개별 JAR 산출물**: `modules/skill-webhook-dispatcher/build/libs/skill-webhook-dispatcher-1.0.0.jar`
- **런타임 외부 의존성**: **Zero** (순수 Java 21 표준 라이브러리 기반)

---

## 1. 주요 제공 기능 (Feature Highlights)

1. **HMAC-SHA256 서명 & 검증 (`WebhookSigner`)**:
   - `X-Signature` 및 GitHub 규격 `X-Hub-Signature-256` (`sha256=...`) 헤더 지원.
   - 타이밍 공격 방지용 상수 시간(`MessageDigest.isEqual`) 서명 검증.
2. **비동기 큐 & 가상 스레드 디스패치 (`WebhookDispatcher`)**:
   - 논블로킹 비동기 전송(`dispatch`, `enqueue`) 및 `CompletableFuture<WebhookDelivery>` 반환.
3. **지수 백오프 재시도 (`RetryPolicy`)**:
   - 5xx 서버 에러 또는 네트워크 결함 시 지수 증가 딜레이(Exponential Backoff)를 적용한 자동 재시도.
4. **배달 라이프사이클 관찰자 (`WebhookListener`)**:
   - `onSuccess`, `onFailure`, `onDeadLetter` 콜백 제공.
5. **Dead-Letter Queue (DLQ)**:
   - 최대 재시도 횟수를 소진한 실패 웹훅 이벤트를 안전하게 격리 보관하고 추후 일괄 인출(`drainDeadLetters()`) 지원.

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.webhook.*;
import java.time.Duration;

// 1. 디스패처 설정 및 빌드
try (WebhookDispatcher dispatcher = WebhookDispatcher.builder()
        .secret("my-shared-webhook-secret")
        .retryPolicy(RetryPolicy.of(3, Duration.ofMillis(200), 2.0, Duration.ofSeconds(5)))
        .addListener(new WebhookListener() {
            @Override
            public void onSuccess(WebhookDelivery delivery) {
                System.out.println("웹훅 전송 성공: " + delivery.event().topic());
            }

            @Override
            public void onDeadLetter(WebhookDelivery delivery, Throwable finalCause) {
                System.err.println("최대 재시도 초과! DLQ 보관: " + delivery.event().id());
            }
        })
        .build()) {

    // 2. 웹훅 이벤트 생성 및 비동기 발송
    WebhookEvent event = Webhook.event("order.paid", "{\"orderId\":\"ORD-9912\",\"amount\":45000}");
    dispatcher.dispatch("https://api.partner.com/webhooks", event)
        .thenAccept(delivery -> {
            System.out.println("최종 배달 상태: " + delivery.statusCode());
        });

    // 3. DLQ 실패 건 조회 및 배수
    if (dispatcher.deadLetterCount() > 0) {
        var failedList = dispatcher.drainDeadLetters();
        System.out.println("격리된 실패 웹훅 건수: " + failedList.size());
    }
}
```
