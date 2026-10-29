# ⏱️ Skill Debounce Throttle (`skill-debounce-throttle`)

High-frequency action debouncer and throttler for Java 21 standard library. Zero external dependencies.

---

## 📌 Features

- **`Debouncer`**:
  - Trailing edge (default): postpones execution until quiet duration has passed without events.
  - Leading edge: triggers immediately on the first event, suppresses execution until quiet duration has passed.
  - Granular cancellation (`cancel(key)`, `cancelAll()`), state introspection (`hasPending(key)`).
- **`Throttler`**:
  - `ThrottleMode.LEADING`: rate-limits calls to at most once per interval, firing immediately.
  - `ThrottleMode.TRAILING`: defers execution to the end of the interval, invoking only the most recent task.
  - `ThrottleMode.LEADING_AND_TRAILING`: fires first call immediately, then fires latest queued task at the interval boundary.
- **Thread-safe**: Uses fine-grained concurrent maps and scheduled executors.
- **Pure Java 21**: No third-party runtime dependencies.

---

## 🚀 Quick Start

### 1. Debouncing (Trailing & Leading)

```java
import com.msp.flow.Debouncer;
import com.msp.flow.DebounceMode;
import java.time.Duration;

try (Debouncer debouncer = new Debouncer()) {
    // Search input field simulation (trailing edge)
    debouncer.debounce("search-input", Duration.ofMillis(300), () -> {
        System.out.println("Executing search query...");
    });

    // Button double-click shield (leading edge)
    debouncer.debounce("submit-btn", Duration.ofSeconds(1), DebounceMode.LEADING, () -> {
        System.out.println("Submitting order once!");
    });
}
```

### 2. Throttling (Rate Limiting)

```java
import com.msp.flow.Throttler;
import com.msp.flow.ThrottleMode;
import java.time.Duration;

try (Throttler throttler = new Throttler()) {
    // Window resize / scroll handler (leading edge)
    boolean executed = throttler.throttle("scroll:window", Duration.ofMillis(100), () -> {
        System.out.println("Repainting scroll position...");
    });

    // Trailing edge: guarantee latest progress update is persisted
    throttler.throttle("progress:job-1", Duration.ofMillis(200), ThrottleMode.TRAILING, () -> {
        System.out.println("Syncing latest progress to DB...");
    });
}
```
