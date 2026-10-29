# 📦 Skill Async Batcher (`skill-async-batcher`)

Micro-batching buffer grouping high-frequency items by batch size or max wait duration. Zero external dependencies.

---

## 📌 Features

- **Size & Timeout Triggers**: Automatically flushes when `maxBatchSize` is reached OR `maxWaitDuration` timer expires.
- **Thread-Safe**: Safe for concurrent submissions across high-throughput producer threads.
- **Orderly Delivery**: Dispatches batches sequentially in FIFO order to the target consumer.
- **Graceful Shutdown**: Automatically flushes all buffered items on `close()` / `flush()`.
- **Pure Java 21**: Built on Java 21 standard library concurrency constructs.

---

## 🚀 Quick Start

```java
import com.msp.batcher.AsyncBatcher;
import java.time.Duration;
import java.util.List;

try (AsyncBatcher<String> batcher = AsyncBatcher.<String>builder()
        .maxBatchSize(50)
        .maxWaitDuration(Duration.ofMillis(100))
        .consumer(batch -> {
            System.out.println("Processing batch of " + batch.size() + " items: " + batch);
        })
        .errorHandler((failedBatch, error) -> {
            System.err.println("Batch error: " + error.getMessage());
        })
        .build()) {

    // Producers submit items concurrently
    batcher.submit("log-event-1");
    batcher.submit("log-event-2");

    // Force flush if needed
    batcher.flush();
} // Automatically flushes remaining items on close()
```
