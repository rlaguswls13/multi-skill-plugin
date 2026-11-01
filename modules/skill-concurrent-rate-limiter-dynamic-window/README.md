# Concurrent Rate Limiter (Dynamic Window AIMD) (`skill-concurrent-rate-limiter-dynamic-window`)

Self-adaptive Rate Limiter dynamically tuning window capacity via **Additive Increase / Multiplicative Decrease (AIMD)** based on downstream latency or failure feedback.

Built with 100% lock-free atomic concurrency for ultra-high throughput in distributed client or microservice egress proxies.

## Features
- **AIMD Adaptive Tuning**: Dynamically expands capacity during healthy response times and throttles back aggressively upon latency spikes or downstream failures.
- **Lock-Free Concurrency**: Uses `AtomicLong` CAS loops for zero thread-blocking during permit acquisition.
- **Strict Bounds Protection**: Clamps window capacity securely within `[minPermits, maxPermits]`.
- **Sliding/Rolling Window**: Automatically advances time windows without background housekeeping threads.
- **Zero Dependencies**: Pure Java 21 standard library.

## Usage

```java
import com.msp.dynamiclimiter.DynamicWindowRateLimiter;
import java.time.Duration;

public class Example {
    public static void main(String[] args) {
        // Min: 5, Max: 100, Initial: 20 permits, Window: 1 second
        DynamicWindowRateLimiter limiter = new DynamicWindowRateLimiter(
                5, 100, 20, Duration.ofSeconds(1)
        );

        // 1. Acquire permit
        if (limiter.tryAcquire()) {
            long start = System.nanoTime();
            try {
                // Call downstream service
                doCall();
                Duration latency = Duration.ofNanos(System.nanoTime() - start);

                // Healthy response under 50ms target -> capacity increases (+1)
                limiter.recordFeedback(true, latency, Duration.ofMillis(50));
            } catch (Exception e) {
                // Failure -> capacity decreases (* 0.5)
                limiter.recordFeedback(false, Duration.ofSeconds(1), Duration.ofMillis(50));
            }
        } else {
            System.out.println("Rate limit exceeded. Request queued or rejected.");
        }
    }

    private static void doCall() {
        // ...
    }
}
```
