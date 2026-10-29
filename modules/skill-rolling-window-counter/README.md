# ⏱️ skill-rolling-window-counter

> **High-Performance Sliding Time-Window Counter with Sub-Bucket Granularity & Spike Detection**  
> Pure Java 21 standard library, lock-free circular ring buffer with striped `LongAdder` cells for zero-thread rate tracking and anomaly surge detection.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Standard Java 21 only (`AtomicReferenceArray`, `LongAdder`).
- **Lock-Free Striped Circular Buffer**: Eliminates thread contention during high-volume event recording.
- **Zero Background Threads**: Stale sub-buckets slide out automatically during read operations.
- **Accurate Rate Metrics**: Direct access to `ratePerSecond()` and `ratePerMinute()`.
- **Statistical Spike Detection (`isSpike`)**: Compares active bucket volume against prior window moving average with configurable multiplier thresholds.
- **Deterministic Time Source**: Supports custom time suppliers and `java.time.Clock` for predictable testing.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-rolling-window-counter:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Rolling Window Rate Tracking

```java
import com.msp.window.RollingWindowCounter;
import java.time.Duration;

// 1-minute window partitioned into 60 1-second buckets
RollingWindowCounter requestCounter = RollingWindowCounter.perMinute(60);

// Record incoming HTTP requests
requestCounter.record();
requestCounter.record(10); // Batch recording

// Inspect metrics
long totalRequests = requestCounter.sum();
double rps = requestCounter.ratePerSecond();
double rpm = requestCounter.ratePerMinute();

System.out.printf("Total: %d, RPS: %.2f, RPM: %.2f%n", totalRequests, rps, rpm);
```

### 2. Sudden Traffic Spike Detection

```java
import com.msp.window.RollingWindowCounter;
import java.time.Duration;

// 10-second rolling window partitioned into 10 sub-buckets
RollingWindowCounter counter = RollingWindowCounter.of(Duration.ofSeconds(10), 10);

// Normal traffic recording...
counter.record(10);

// Check if current 1-second slice represents a 3x traffic surge compared to baseline
if (counter.isSpike(3.0)) {
    System.err.println("ALERT: Sudden traffic surge detected! Initiating rate limiting.");
}
```
