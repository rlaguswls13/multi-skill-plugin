# skill-concurrent-adaptive-concurrency-vegas

Zero-dependency Java 21 adaptive concurrency limiter implementing **TCP Vegas** and **Gradient RTT** control algorithms with an injectable monotonic clock.

## Architecture Highlights
- **Sealed `LimitAlgorithm`**:
  - `VegasAlgorithm`: Tracks `minRtt`, estimates queue size `limit * (1 - minRtt / rtt)`, increases when `queueSize <= alpha`, decreases when `queueSize >= beta`, and backs off on dropped requests.
  - `GradientAlgorithm`: Tracks slow EWMA `longRtt` vs current RTT; scales concurrency smoothly when latency diverges beyond `rttTolerance`.
- **Non-Blocking `ConcurrencyLimiter`**:
  - Lock-free `inFlight` tracking with idempotent `Listener` (`onSuccess()`, `onDropped()`, `onIgnore()`).
  - Injectable `LongSupplier nanoClock` for 100% deterministic unit tests.
  - Point-in-time `MetricsSnapshot` record for observability.

## Usage Example
```java
import com.msp.vegaslimit.*;
import java.util.Optional;

VegasAlgorithm vegas = VegasAlgorithm.builder()
        .initialLimit(20)
        .minLimit(4)
        .maxLimit(200)
        .alpha(3.0)
        .beta(6.0)
        .build();

ConcurrencyLimiter limiter = new ConcurrencyLimiter(vegas);

Optional<ConcurrencyLimiter.Listener> permit = limiter.tryAcquire();
if (permit.isEmpty()) {
    throw new IllegalStateException("Shedding load: concurrency limit reached");
}
ConcurrencyLimiter.Listener listener = permit.get();
try {
    // execute downstream call
    listener.onSuccess();
} catch (RuntimeException ex) {
    listener.onDropped();
    throw ex;
}
```
