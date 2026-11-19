# skill-concurrent-retry-dead-letter-queue

Dead-Letter Queue (DLQ) Manager & Poison Message Quarantine Orchestrator in pure Java 21.

## 🚀 Features

- **Poison Message Quarantine**: Safely isolates messages that repeatedly fail processing after retries are exhausted.
- **Rich Diagnostics Metadata**: Captures error type, message, full stack trace, attempt counts, quarantine timestamp, and custom metadata.
- **Pluggable Persistence**:
  - `InMemoryDeadLetterStore`: High-performance concurrent in-memory storage using `ConcurrentHashMap`.
  - `FileDeadLetterStore`: Atomic file append and re-write persistence with zero external serialization dependencies.
- **Search & Inspection**: Filter by error type, time range, payload keyword, or arbitrary predicates.
- **Reprocessing & Replay Orchestration**:
  - `replay(Consumer<DeadLetterMessage<T>> retryHandler)`: Iterates and reprocesses messages. Successful runs mark messages as `REPLAYED`. Failed runs increment attempts.
  - `replayOne(id, retryHandler)` and `replayBatch(ids, retryHandler)`.
- **Quarantine Metrics**: Thread-safe atomic counters tracking `totalQuarantined`, `activeQuarantined`, `replaySuccessCount`, `replayFailureCount`, and `discardedCount`.
- **Zero Runtime Dependencies**: Pure Java 21 standard library (`java.util.concurrent.*`, `java.nio.file.*`).

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation project(':modules:skill-concurrent-retry-dead-letter-queue')
}
```

## 💡 Quick Start

```java
import com.msp.dlq.ConcurrentDeadLetterQueue;
import com.msp.dlq.DeadLetterQueue;
import com.msp.dlq.DlqMetrics;
import java.util.Map;

public class Example {
    public static void main(String[] args) {
        DeadLetterQueue<String> dlq = ConcurrentDeadLetterQueue.inMemory();

        // 1. Enqueue failed poison message
        try {
            processPayment("order-9872");
        } catch (Exception ex) {
            dlq.enqueue("order-9872", ex, 3, Map.of("source", "checkout", "channel", "web"));
        }

        // 2. Inspect quarantined items
        System.out.println("Active quarantined: " + dlq.size());

        // 3. Replay failed messages
        dlq.replay(msg -> {
            System.out.println("Retrying message: " + msg.payload());
            // Retry logic here...
        });

        // 4. Check metrics
        DlqMetrics metrics = dlq.metrics();
        System.out.println("Replay success: " + metrics.replaySuccessCount());
    }

    private static void processPayment(String orderId) {
        throw new IllegalStateException("Payment gateway unreachable");
    }
}
```
