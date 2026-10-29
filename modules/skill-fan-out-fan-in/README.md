# 🌐 skill-fan-out-fan-in

> **Virtual Thread Parallel Task Distributor (Fan-out) & Aggregator (Fan-in) for Java 21**  
> Run hundreds or thousands of lightweight concurrent tasks on Java 21 Virtual Threads, collect results, handle timeouts, and aggregate partial failures cleanly.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library (`Executors.newThreadPerTaskExecutor()`, `Thread.ofVirtual()`).
- **High Concurrency**: Spawns non-blocking virtual threads for each task without exhausting OS thread pools.
- **Aggregated Outcome (`FanInResult<T>`)**:
  - `successfulResults()`: Ordered list of successfully computed values.
  - `failures()` & `failureMap()`: Captured exceptions indexed by task position.
  - `executionDuration()`: Exact elapsed wall-clock duration.
- **Safety**:
  - Configurable execution deadline via `.timeout(Duration)`.
  - Optional `.failFast(true)` aborts lingering tasks upon first encountered error.
  - Custom virtual thread naming prefix via `.namePrefix(prefix)`.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-fan-out-fan-in:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Basic Fan-Out / Fan-In

```java
import com.msp.fanout.FanOutOrchestrator;
import com.msp.fanout.FanInResult;
import java.time.Duration;
import java.util.List;

// Distribute multiple RPC / DB calls across virtual threads
FanInResult<String> result = FanOutOrchestrator.fanOut(
        () -> fetchUserProfile("user-1"),
        () -> fetchOrderHistory("user-1"),
        () -> fetchAccountBalance("user-1")
)
.timeout(Duration.ofSeconds(2))
.execute();

// Check overall status
if (result.isAllSuccessful()) {
    List<String> responses = result.successfulResults();
    System.out.println("All tasks succeeded: " + responses);
} else {
    System.err.println("Some tasks failed: " + result.failures());
}
```

### 2. Handling Partial Failures and Timeouts

```java
FanInResult<DataChunk> result = FanOutOrchestrator.fanOut(downloadTasks)
        .timeout(Duration.ofMillis(500))
        .namePrefix("downloader-vthread-")
        .execute();

System.out.printf("Finished in %d ms (%d succeeded, %d failed)%n",
        result.executionDuration().toMillis(),
        result.successCount(),
        result.failureCount());

// Inspect which tasks failed
result.failureMap().forEach((index, error) -> {
    System.err.printf("Task #%d failed due to: %s%n", index, error.getMessage());
});
```
