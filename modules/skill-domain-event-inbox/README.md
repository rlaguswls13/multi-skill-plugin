# Transactional Domain Event Inbox (`skill-domain-event-inbox`)

카프카(Kafka), RabbitMQ, SQS 등 분산 메시징 환경에서 At-least-once 전달로 인해 발생하는 중복 메시지 소비를 방지하고 멱등성(Idempotency)을 보장하는 인박스 패턴 모듈입니다.

## Features
- **Atomic Concurrency Control**: `ConcurrentHashMap` 기반 원자적 상태 전이(`PENDING` -> `PROCESSED` / `FAILED`).
- **Dead Lock Prevention**: 처리 도중 인스턴스가 비정상 종료된 경우를 대비한 PENDING 상태 타임아웃 자동 재획득 지원.
- **Retry on Failure**: 실패한 이벤트에 대해 원인 기록 및 재시도 획득 허용.
- **Zero Dependencies**: 순수 Java 21 표준 라이브러리(`java.time`, `java.util.concurrent`).

## Usage

```java
EventInbox inbox = new EventInbox();

// 이벤트 수신 리스너
if (!inbox.tryAcquire(event.getId(), event.getType(), event.getPayload())) {
    log.info("Duplicate event ignored: {}", event.getId());
    return;
}

try {
    processBusinessLogic(event);
    inbox.markProcessed(event.getId());
} catch (Exception e) {
    inbox.markFailed(event.getId(), e.getMessage());
    throw e;
}
```
