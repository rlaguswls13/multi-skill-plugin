# 🛑 skill-graceful-shutdown

> **JVM Shutdown Hook Coordinator with Ordered Teardown & Timeout Safety for Java 21**  
> Coordinates phased, orderly cleanup of services, connection pools, and worker threads during JVM shutdown without freezing or deadlocking.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library (`Runtime.getRuntime().addShutdownHook()`, `Thread.ofVirtual()`).
- **Phased Reverse-Dependency Teardown**:
  - Components are executed in numeric order (Order 1 -&gt; Order 2 -&gt; Order 3).
  - Example: Phase 1 (Stop web listener) -&gt; Phase 2 (Drain worker queues) -&gt; Phase 3 (Close DB &amp; Cache pools).
  - Same-order components run concurrently on Virtual Threads for fast cleanup.
- **Fail-Safe & Timeout-Bounded**:
  - Exceptions during component teardown are recorded in `ShutdownReport` and never halt the remaining cleanup phases.
  - Strict timeout enforcement prevents unresponsive components from hanging the JVM shutdown process.
- **Thread-Safe & Idempotent**:
  - Guaranteed single execution: redundant shutdown calls safely wait for ongoing teardown.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-graceful-shutdown:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Registering Components with Shutdown Hook

```java
import com.msp.shutdown.GracefulShutdownManager;
import java.time.Duration;

GracefulShutdownManager shutdownManager = new GracefulShutdownManager();

// Phase 1: Stop accepting external HTTP traffic
shutdownManager.registerComponent("HttpServer", 1, () -> {
    httpServer.stop();
});

// Phase 2: Finish in-flight background worker jobs
shutdownManager.registerComponent("WorkerPool", 2, () -> {
    workerPool.stop();
    workerPool.awaitTermination(Duration.ofSeconds(3));
});

// Phase 3: Flush caches and close database connection pool
shutdownManager.registerComponent("Cache", 3, cache::flush);
shutdownManager.registerComponent("DataSource", 3, dataSource::close);

// Register JVM shutdown hook (bounds total cleanup to 10 seconds)
shutdownManager.addShutdownHook(Duration.ofSeconds(10));
```

### 2. Programmatic Shutdown with Detailed Report

```java
ShutdownReport report = shutdownManager.initiateShutdown(Duration.ofSeconds(5));

System.out.println("Shutdown successful: " + report.isSuccessful());
System.out.println("Total cleanup time: " + report.totalDuration().toMillis() + " ms");

report.componentResults().forEach(res -> {
    System.out.printf("Component [%s] (Phase %d): %s (took %d ms)%n",
            res.name(), res.order(), res.isSuccess() ? "OK" : "FAILED", res.duration().toMillis());
});
```
