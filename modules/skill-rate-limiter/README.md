# ⏱️ skill-rate-limiter

> **High-Performance, Zero-Dependency Rate Limiting Library for Java 21**  
> Provides Thread-Safe **Token Bucket** (with burst capability) and **Sliding Window Counter** rate limiters with non-blocking and timeout-based permit acquisition.

---

## 🚀 Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library (`java.util.concurrent.locks.ReentrantLock`).
- **Token Bucket Algorithm**:
  - Configurable burst capacity and continuous refill rate (tokens/second or tokens/period).
  - Smooth nano-second granularity refill without background timer threads.
- **Sliding Window Counter Algorithm**:
  - Divided sub-slice counters avoiding the "boundary burst" flaw of fixed window limiters.
  - Constant memory $O(N)$ with configurable slice precision.
- **Flexible Acquisition**:
  - Non-blocking: `tryAcquire()`, `tryAcquire(permits)`
  - Timeout-based: `tryAcquire(timeout)`, `tryAcquire(permits, timeout)`
  - Blocking: `acquire()`, `acquire(permits)`
- **Unified Factory**: `RateLimiters` facade.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-rate-limiter:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Token Bucket (Burst Allowance + Refill)

```java
import com.msp.ratelimit.RateLimiter;
import com.msp.ratelimit.RateLimiters;
import java.time.Duration;

// Allow burst up to 10 requests, refilling at 5 requests per second
RateLimiter limiter = RateLimiters.tokenBucket(10, 5.0);

// Non-blocking try
if (limiter.tryAcquire()) {
    processRequest();
} else {
    // 429 Too Many Requests
    rejectRequest();
}

// Blocking with 500ms timeout
if (limiter.tryAcquire(Duration.ofMillis(500))) {
    processRequest();
}
```

### 2. Sliding Window Counter (Smooth Rate Capping)

```java
import com.msp.ratelimit.RateLimiter;
import com.msp.ratelimit.RateLimiters;
import java.time.Duration;

// Allow maximum 100 requests per 1-minute window
RateLimiter limiter = RateLimiters.slidingWindow(100, Duration.ofMinutes(1));

if (limiter.tryAcquire()) {
    executeApiCall();
}
```
