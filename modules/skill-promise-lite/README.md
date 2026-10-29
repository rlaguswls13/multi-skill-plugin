# 🤝 Skill Promise Lite (`skill-promise-lite`)

Fluent JavaScript-style Promise abstraction wrapping Java 21's `CompletableFuture` with `allOf`, `anyOf`, `race`, and timeout resilience. Zero external dependencies.

---

## 📌 Features

- **Fluent Chaining**: `thenApply`, `thenCompose`, `thenAccept`, `thenRun`, `finallyDo`.
- **First-Class Error Recovery**: `catchError(fallback)`, `catchError(SpecificException.class, fallback)`.
- **Timeout Management**: `timeout(Duration)`, `timeout(Duration, fallbackValue)`, `timeout(Duration, fallbackSupplier)`.
- **Standard Combinators**:
  - `allOf`: waits for all promises, preserving order; fails fast on error.
  - `anyOf`: resolves with first successful promise, rejecting with `AggregateException` only if all fail.
  - `race`: settles immediately with the fastest promise (success or error).
- **Constructors**: `Promise.resolve()`, `Promise.reject()`, `Promise.supplyAsync()`, `Promise.create((res, rej) -> ...)`.

---

## 🚀 Quick Start

### 1. Fluent Chaining & Recovery

```java
import com.msp.promise.Promise;
import java.time.Duration;

Promise<String> result = Promise.supplyAsync(() -> fetchRemoteUser(42))
        .thenApply(user -> user.toUpperCase())
        .timeout(Duration.ofMillis(250), "ANONYMOUS_USER")
        .catchError(IllegalArgumentException.class, ex -> "DEFAULT_USER");

System.out.println(result.join());
```

### 2. Combinators (`allOf`, `anyOf`, `race`)

```java
Promise<String> p1 = Promise.supplyAsync(() -> callServiceA());
Promise<String> p2 = Promise.supplyAsync(() -> callServiceB());

// JavaScript-style Promise.any (first fulfillment)
Promise<Object> fastestSuccess = Promise.anyOf(p1, p2);

// Promise.race (first completion, whether fulfilled or rejected)
Promise<Object> winner = Promise.race(p1, p2);

// Promise.allOf (wait for all)
Promise<List<Object>> allResults = Promise.allOf(p1, p2);
```
