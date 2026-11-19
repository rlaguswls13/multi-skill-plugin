# skill-process-posix-signal-notifier

> JVM Graceful Shutdown & POSIX OS Signal (SIGINT, SIGTERM, SIGHUP, SIGQUIT) Asynchronous Notification Registry.

---

## 🚀 Features

- **POSIX Signal Listener Registry**:
  - Supports `SIGINT` (Ctrl+C), `SIGTERM` (Termination / Container stop), `SIGHUP` (Reload), and `SIGQUIT`.
  - Prioritized dispatch: High-priority shutdown tasks (e.g. stop ingress traffic) run before low-priority ones (e.g. close DB connections).
  - FIFO tie-breaking for equal priorities.
- **Per-Listener Timeout Guard**:
  - Configurable execution timeout per listener. If a slow or hanging component exceeds its deadline, it is recorded as `TIMEOUT`, isolated, and subsequent listeners continue without freezing JVM shutdown.
- **Detailed Execution Reporting**:
  - `SignalDispatchReport`: captures start/end timestamps, total duration, and each listener's execution status (`SUCCESS`, `TIMEOUT`, `FAILED`) and errors.
- **Multi-Platform JVM Shutdown Hook**:
  - Automatic bridge between OS termination signals and JVM shutdown hooks across Windows, Linux, and macOS.
- **Pure Java 21**: Zero runtime dependencies.

---

## 📦 Usage Examples

### 1. Registering Prioritized Shutdown Listeners

```java
import com.msp.signotifier.*;
import java.time.Duration;

SignalNotifier notifier = SignalNotifier.getInstance();

// 1. High Priority (10): Stop accepting new HTTP requests
notifier.registerListener("HttpServer", SignalType.SIGTERM, event -> {
    System.out.println("Stopping HTTP server listener...");
}, 10, Duration.ofSeconds(2));

// 2. Medium Priority (5): Flush write-behind caches
notifier.registerListener("CacheFlusher", SignalType.SIGTERM, event -> {
    System.out.println("Flushing dirty cache entries to disk...");
}, 5, Duration.ofSeconds(3));

// 3. Low Priority (1): Close database connection pools
notifier.registerListener("DatabasePool", SignalType.SIGTERM, event -> {
    System.out.println("Closing database connection pools...");
}, 1, Duration.ofSeconds(2));

// Enable JVM shutdown hook integration
notifier.enableShutdownHook();
```

### 2. Handling Configuration Reload on SIGHUP

```java
notifier.registerListener("ConfigReloader", SignalType.SIGHUP, event -> {
    System.out.println("Reloading application configuration...");
    reloadConfigFromDisk();
});

// Programmatic signal dispatch for testing or admin endpoints
SignalDispatchReport report = notifier.notifySignal(SignalType.SIGHUP);
System.out.printf("Dispatched to %d listeners in %d ms (all success: %b)%n",
        report.getTotalListeners(),
        report.totalDuration().toMillis(),
        report.isAllSuccess());
```
