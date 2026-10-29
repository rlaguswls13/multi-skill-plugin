# 🔒 skill-concurrency-locker

> **Thread-Safe Key-Based Locking Library for Java 21**  
> Provides granular in-memory locking per key (e.g. `userId`, `orderId`, `accountId`) with automatic reference-counting garbage collection of unused locks to prevent memory leaks.

---

## 🚀 Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library (`ReentrantLock`, `ConcurrentHashMap`).
- **Granular Mutual Exclusion**:
  - Different keys run concurrently in parallel without contention.
  - The same key executes strictly sequentially.
- **Zero Memory Leak Guarantee**:
  - Uses atomic reference counting (`RefCountKeyedLocker`).
  - As soon as the last thread releases or times out on a key, the lock entry is removed from the internal map immediately.
- **Timeout Support**:
  - `withLock(key, timeout, action)` throws `LockTimeoutException` on timeout without leaving orphan locks behind.
- **Bounded Striped Alternative**:
  - `StripedKeyedLocker` provides constant-memory striped locking across fixed power-of-two stripes.
- **Unified Factory**: `KeyedLockers` facade.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-concurrency-locker:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Keyed Locking with Return Value

```java
import com.msp.locker.KeyedLocker;
import com.msp.locker.KeyedLockers;

KeyedLocker<String> locker = KeyedLockers.create();

// Thread-safe deduction per user account
String receipt = locker.withLock("user-1004", () -> {
    Account account = accountRepo.findById("user-1004");
    account.deduct(500);
    return accountRepo.save(account);
});
```

### 2. Lock Acquisition with Timeout

```java
import com.msp.locker.KeyedLocker;
import com.msp.locker.KeyedLockers;
import com.msp.locker.LockTimeoutException;
import java.time.Duration;

KeyedLocker<Long> orderLocker = KeyedLockers.create();

try {
    orderLocker.withLock(orderId, Duration.ofMillis(500), () -> {
        processOrder(orderId);
    });
} catch (LockTimeoutException e) {
    // Graceful handling of concurrent conflict
    log.warn("Order {} is currently locked by another operation", orderId);
}
```
