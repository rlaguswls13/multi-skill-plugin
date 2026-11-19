# skill-concurrent-priority-executor

> Priority and Time-To-Live (TTL) based concurrency task executor for background async workloads.

---

## 🚀 Features

- **Strict Priority Ordering**:
  - Task priority range: `1` (lowest) to `10` (highest, default `5`).
  - Higher priority tasks execute ahead of lower priority tasks waiting in the queue.
- **FIFO Tie-Breaking**:
  - Stable sequencing guarantees that tasks with equal priority are dispatched in first-in-first-out order.
- **Queue TTL Expiration**:
  - Optional per-task TTL deadline. If a task waits in queue beyond its deadline before a worker picks it up, it is rejected and completed exceptionally with `TaskExpiredException`.
- **Modern Async API**:
  - Returns standard Java `CompletableFuture<T>` or `CompletableFuture<Void>`.
- **Observability**:
  - `getActiveCount()`, `getQueueDepth()`, `getExpiredTaskCount()`, `getCompletedTaskCount()`, `getSubmittedTaskCount()`.
- **Lifecycle & Graceful Shutdown**:
  - Implements `AutoCloseable` with configurable termination timeouts.
- **Pure Java 21**: Built entirely on `java.util.concurrent.*` standard library with zero external dependencies.

---

## 📦 Usage Examples

### 1. Submitting Tasks with Priorities

```java
import com.msp.priorexec.PriorityTaskExecutor;
import java.util.concurrent.CompletableFuture;

try (PriorityTaskExecutor executor = PriorityTaskExecutor.newFixedThreadPool(4)) {
    // High-priority urgent task (Priority 9)
    CompletableFuture<String> urgent = executor.submit(() -> "Critical Report", 9);

    // Standard background task (Priority 5, default)
    CompletableFuture<Void> background = executor.run(() -> System.out.println("Syncing cache..."));

    // Low-priority cleanup task (Priority 2)
    CompletableFuture<Void> cleanup = executor.run(() -> System.out.println("Purging old logs..."), 2);

    System.out.println(urgent.join());
}
```

### 2. Guarding against Stale Tasks with TTL

```java
import com.msp.priorexec.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

try (PriorityTaskExecutor executor = PriorityTaskExecutor.newFixedThreadPool(2)) {
    // Task expires if it sits in the queue longer than 500ms
    CompletableFuture<String> future = executor.submit(
            () -> queryRealtimeSensor(),
            7,
            Duration.ofMillis(500)
    );

    future.exceptionally(ex -> {
        if (ex.getCause() instanceof TaskExpiredException expired) {
            System.err.println("Task expired after waiting " + expired.getWaitedDuration().toMillis() + " ms");
        }
        return "FALLBACK_VALUE";
    });
}
```
