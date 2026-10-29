# ✈️ Skill Single Flight (`skill-single-flight`)

Duplicate in-flight execution suppressor (Go-style `singleflight.Group`) for Java 21 standard library, eliminating Cache Stampede (Thundering Herd). Zero external dependencies.

---

## 📌 Features

- **Cache Stampede Prevention**: When N concurrent threads request the same key, exactly ONE executes the supplier while the other N-1 await and share the result.
- **Result Introspection**: `executeWithResult()` returns `SingleFlightResult<V>(value, shared)` letting callers know if they executed or shared.
- **Exception Broadcast**: If the leader callable fails, all awaiting callers receive the identical exception, and the in-flight state is cleaned up for subsequent retries.
- **Async Execution**: `executeAsync(key, supplier)` for non-blocking reactive workflows.
- **Manual Eviction**: `forget(key)` removes active in-flight tracking on-demand.

---

## 🚀 Quick Start

```java
import com.msp.singleflight.SingleFlight;
import com.msp.singleflight.SingleFlightResult;

SingleFlight<String, UserProfile> flight = SingleFlight.create();

// 50 threads can execute this concurrently:
// Only 1 database call will happen; all 50 threads get the exact same profile!
UserProfile profile = flight.execute("user:1001", () -> {
    return database.fetchUserProfile("1001");
});

// Or inspect whether the execution was shared:
SingleFlightResult<UserProfile> res = flight.executeWithResult("user:1001", () -> {
    return database.fetchUserProfile("1001");
});
System.out.println("Value: " + res.value() + ", Shared from another thread: " + res.shared());
```
