# Leaky Bucket Rate Limiter (`skill-rate-limiter-token-leaky-bucket`)

Zero-dependency, thread-safe Leaky Bucket rate limiter and traffic smoother with nanosecond precision in pure Java 21.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **Traffic Smoothing**: Absorbs sudden bursts up to `capacity` and regulates outflow at a constant `leakRatePerSecond`.
- **Non-blocking & Blocking Modes**: Supports immediate non-blocking checks (`tryAcquire()`) and smooth blocking backpressure (`acquire()`).
- **Thread-Safe**: Accurate concurrent synchronization via `ReentrantLock`.
- **High-Precision Time Tracking**: Uses `System.nanoTime()` to avoid drift across clock updates.

---

## 📦 Usage Examples

### 1. Non-blocking Rate Limiting (tryAcquire)

```java
import com.msp.leakybucket.LeakyBucketRateLimiter;

// Bucket capacity: 10 requests, Leak rate: 5 requests per second
LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(10, 5.0);

if (limiter.tryAcquire()) {
    // Process request
    System.out.println("Request allowed, current level: " + limiter.currentLevel());
} else {
    // 429 Too Many Requests
    System.out.println("Rate limit exceeded!");
}
```

### 2. Blocking Backpressure (acquire)

```java
// Smooth out upstream jobs with automatic sleep until bucket has capacity
try {
    limiter.acquire();
    sendDownstreamEvent();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```
