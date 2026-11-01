# 🧩 skill-concurrent-event-dedup-lru

> High-throughput striped-locking LRU sliding window idempotency event deduplicator with zero 3rd-party dependencies.

---

## 🌟 Key Features

- **Striped-Locking Architecture**: Divides cache across configurable striped lock segments (default 16) to virtually eliminate lock contention under heavy concurrency.
- **LRU Sliding Window Eviction**: Constrains memory footprint via `LinkedHashMap` access-ordered LRU eviction.
- **TTL Expiration**: Automatically invalidates events older than the configured `Duration`.
- **In-Flight & Retry Lifecycle**:
  - `tryAcquire(eventId)`: Registers unique in-flight event.
  - `markProcessed(eventId)`: Confirms successful processing, preserving duplicate protection until TTL.
  - `release(eventId)`: Cancels in-flight status on failure so downstream consumers can retry immediately.
- **Background Purge Support**: `cleanupExpired()` sweeps stale events across segments.
- **Zero Runtime Dependencies**: Pure Java 21 standard library.

---

## 🚀 Quick Start

```java
import com.msp.eventdedup.EventDeduplicator;
import java.time.Duration;

// Cache up to 100,000 events with 5-minute sliding TTL across 16 stripes
EventDeduplicator dedup = new EventDeduplicator(100_000, Duration.ofMinutes(5), 16);

String eventId = "order-created-984124";

if (dedup.tryAcquire(eventId)) {
    try {
        // Process message or transaction...
        processEvent(eventId);
        
        // Mark as successfully processed
        dedup.markProcessed(eventId);
    } catch (Exception ex) {
        // On transient failure, release so event can be retried immediately
        dedup.release(eventId);
        throw ex;
    }
} else {
    // Duplicate detected (in flight or within TTL)
    System.out.println("Duplicate or in-flight event ignored: " + eventId);
}
```

---

## 🧪 Testing

```bash
.\gradlew.bat :modules:skill-concurrent-event-dedup-lru:test
```
