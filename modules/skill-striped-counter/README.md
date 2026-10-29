# 🔢 skill-striped-counter

> **Ultra-High Concurrency Striped Counter & Sliding-Window Rate Tracker for Java 21**  
> Provides low-contention atomic counters via cache-line striping and zero-thread sliding-window rate tracking.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library (`LongAdder`, `AtomicLong`).
- **`StripedCounter`**:
  - Distributes increments across power-of-two stripes with thread ID hash distribution.
  - Eliminates CAS retry storms and cache-line invalidation under 100+ concurrent threads.
  - Implements `Number` (`intValue()`, `longValue()`, `doubleValue()`).
  - Supports `sumThenReset()` and `reset()`.
- **`SlidingWindowCounter`**:
  - Sub-bucket circular ring buffer for real-time sliding rate calculation.
  - Accurate calculation of rate per second (`getRatePerSecond()`) and rate per minute (`getRatePerMinute()`).
  - Zero background daemon threads: stale buckets slide out automatically on access.
  - Deterministic clock injection for unit testing.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-striped-counter:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Ultra-High Concurrency StripedCounter

```java
import com.msp.counter.StripedCounter;

// Create striped counter with default CPU core stripes
StripedCounter counter = new StripedCounter();

// Atomic increments under high concurrency
counter.increment();
counter.increment(5);
counter.decrement();

// Sum total
long total = counter.sum();
System.out.println("Total: " + total);

// Atomic read and reset
long previous = counter.sumThenReset();
```

### 2. Sliding-Window Rate Tracking

```java
import com.msp.counter.SlidingWindowCounter;
import java.time.Duration;

// Track request rates over a 1-minute window partitioned into 60 1-second slices
SlidingWindowCounter rateTracker = SlidingWindowCounter.perMinute(60);

// Record incoming HTTP requests
rateTracker.record();
rateTracker.record(10); // Batch recording

// Query metrics at any moment
long requestsInLastMinute = rateTracker.getTotalCount();
double requestsPerSecond = rateTracker.getRatePerSecond();
double requestsPerMinute = rateTracker.getRatePerMinute();

System.out.printf("Current RPS: %.2f, RPM: %.2f%n", requestsPerSecond, requestsPerMinute);
```
