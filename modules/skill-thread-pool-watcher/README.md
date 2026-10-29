# 🔭 Skill Thread Pool Watcher (`skill-thread-pool-watcher`)

Real-time health monitor, queue saturation analyzer, active worker tracker, and rejection alerter for `ThreadPoolExecutor`. Zero external dependencies.

---

## 📌 Features

- **Queue Saturation Monitoring**: Calculates queue occupancy ratio (`queueSize / totalCapacity`).
- **Active Worker Utilization**: Measures active worker threads relative to `maximumPoolSize`.
- **Rejection Tracking & Alerts**: Automatically wraps `RejectedExecutionHandler` to record rejections and notify listeners.
- **Snapshot Record**: `PoolSnapshot` encapsulates core size, max size, active count, queue remaining capacity, saturation %, and rejection count.
- **Configurable Alert Thresholds**: Notifies custom listeners on `QUEUE_SATURATION`, `ACTIVE_WORKER_SATURATION`, and `TASK_REJECTION`.
- **Periodic Health Check**: Background daemon monitoring with start/stop lifecycle.

---

## 🚀 Quick Start

```java
import com.msp.poolwatch.ThreadPoolWatcher;
import com.msp.poolwatch.ThresholdConfig;
import com.msp.poolwatch.PoolSnapshot;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

ThreadPoolExecutor executor = new ThreadPoolExecutor(
        4, 16, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500)
);

try (ThreadPoolWatcher watcher = ThreadPoolWatcher.builder()
        .name("order-processing-pool")
        .executor(executor)
        .thresholds(new ThresholdConfig(0.80, 0.85)) // 80% queue saturation, 85% worker saturation
        .alertListener((snapshot, reason, current, limit) -> {
            System.err.printf("[ALERT] Pool %s breached %s: current=%.2f, limit=%.2f%n",
                    snapshot.poolName(), reason, current, limit);
        })
        .build()) {

    // Start background health checking every 5 seconds
    watcher.start(Duration.ofSeconds(5));

    // Or take an on-demand snapshot anytime:
    PoolSnapshot snap = watcher.takeSnapshot();
    System.out.printf("Queue Saturation: %.1f%%, Active Workers: %d/%d%n",
            snap.queueSaturationRatio() * 100, snap.activeCount(), snap.maximumPoolSize());
}
```
