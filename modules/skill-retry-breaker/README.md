# 🛡️ skill-retry-breaker

> **Resilience Engineering Library for Java 21**  
> Provides configurable **RetryPolicy** with exponential backoff and jitter, plus a sliding-window **CircuitBreaker** supporting CLOSED, OPEN, and HALF_OPEN recovery states.

---

## 🚀 Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **Configurable RetryPolicy**:
  - Exponential backoff with `initialDelay`, `multiplier`, and `maxDelay`.
  - Jitter algorithms: `NONE`, `FULL`, and `EQUAL` to prevent thundering herd spikes.
  - Predicate filtering via `retryOn(Predicate)` and `abortOn(Predicate)`.
- **Sliding-Window CircuitBreaker**:
  - Ring buffer sliding window metrics.
  - States: `CLOSED` -> `OPEN` (tripped when failure rate exceeds threshold) -> `HALF_OPEN` (provisional trial) -> `CLOSED`.
  - Configurable `failureRateThreshold`, `minimumCalls`, `waitDurationInOpen`, and `permittedCallsInHalfOpen`.
- **Unified Facade**: `Resilience` provides fluent factories and combined execution (`Resilience.executeWith`).

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-retry-breaker:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Exponential Backoff Retry with Full Jitter

```java
import com.msp.resilience.*;
import java.time.Duration;

RetryPolicy policy = RetryPolicy.builder()
        .maxAttempts(4)
        .initialDelay(Duration.ofMillis(200))
        .multiplier(2.0)
        .maxDelay(Duration.ofSeconds(2))
        .jitter(Jitter.FULL)
        .retryOn(IOException.class)
        .build();

RetryExecutor executor = Resilience.retry(policy);

String response = executor.execute(() -> remoteHttpCall());
```

### 2. Circuit Breaker Protection

```java
import com.msp.resilience.*;
import java.time.Duration;

CircuitBreaker cb = Resilience.circuitBreaker("payment-service")
        .failureRateThreshold(50.0)      // Trip if >= 50% fail
        .minimumCalls(10)                // Evaluate after 10 calls
        .waitDurationInOpen(Duration.ofSeconds(10)) // Wait 10s before test probe
        .permittedCallsInHalfOpen(3)     // 3 consecutive successes to recover
        .build();

try {
    String confirmation = cb.execute(() -> processPayment());
} catch (CircuitBreakerOpenException e) {
    // Fast-fail fallback
    fallbackQueue();
}
```
