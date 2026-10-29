# ⚡ skill-virtual-thread-runner

> **Java 21 Virtual Thread Runner with Concurrency Rate-Limiting Semaphore**  
> Enables high-volume asynchronous task execution on Java 21 Virtual Threads while strictly protecting downstream databases and external APIs from stampedes.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library (`Thread.ofVirtual()`, `Semaphore`, `CompletableFuture`).
- **Downstream Resource Protection**:
  - Unbounded virtual threads can easily overwhelm connection pools (e.g. HikariCP with 20 connections) or third-party HTTP limits.
  - Transparently limits concurrent executions via carrier-friendly `Semaphore.acquire()`, unmounting virtual threads while queued.
- **Rich Async Control**:
  - Non-blocking `.submit(Callable<T>)` and `.run(Runnable)` returning `CompletableFuture<T>`.
  - Batch execution via `.submitAll(Collection)` and `.runAll(Collection)`.
  - Non-polling, condition-signaled synchronization via `.awaitAll()` and `.awaitAll(Duration)`.
  - Live inspection: `getActiveCount()`, `getQueuedCount()`, and `getCompletedCount()`.
- **AutoCloseable**: Clean teardown on exit.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-virtual-thread-runner:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Limiting Concurrency to Match Database Pool

```java
import com.msp.vthread.VirtualThreadRunner;
import java.util.concurrent.CompletableFuture;

// Limit concurrent queries to 10 to match DataSource maxPoolSize
try (VirtualThreadRunner runner = VirtualThreadRunner.withMaxConcurrency(10)) {

    // Safely fire off 10,000 asynchronous database queries
    for (int i = 0; i < 10_000; i++) {
        final int userId = i;
        runner.submit(() -> {
            return database.fetchUser(userId);
        });
    }

    // Wait for all 10,000 queries to complete
    runner.awaitAll();
}
```

### 2. Async Webhook / HTTP Calling with Futures

```java
VirtualThreadRunner runner = VirtualThreadRunner.builder()
        .maxConcurrency(50)
        .namePrefix("webhook-caller-")
        .build();

CompletableFuture<HttpResponse> future = runner.submit(() -> {
    return httpClient.post(webhookPayload);
});

future.thenAccept(response -> {
    System.out.println("Webhook responded with: " + response.statusCode());
}).exceptionally(ex -> {
    System.err.println("Webhook failed: " + ex.getMessage());
    return null;
});
```
