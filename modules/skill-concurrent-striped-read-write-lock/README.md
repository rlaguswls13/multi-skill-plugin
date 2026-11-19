# skill-concurrent-striped-read-write-lock

Zero-dependency Java 21 fixed-stripe `ReadWriteLock` manager with power-of-2 segment striping, Murmur3 avalanche hashing, configurable fairness, and deadlock-free sorted multi-key bulk locking.

## Architecture Highlights
- **Bounded Memory**: Pre-allocates a fixed power-of-2 array of `ReentrantReadWriteLock` stripes, eliminating unbounded per-key lock maps and GC churn.
- **Avalanche Hashing**: Applies the Murmur3 32-bit finalizer to `key.hashCode()` before bitmasking (`hash & (stripes - 1)`), ensuring even stripe distribution even for sequential keys.
- **Deadlock-Free Multi-Key Acquisition**: `bulkWriteLock(keys)` and `bulkReadLock(keys)` map all keys to stripe indices, deduplicate, and acquire locks in strictly ascending stripe index order. `BulkLockHandle` implements `AutoCloseable` with idempotent LIFO unlocking.

## Quick Start

```java
import com.msp.stripedlock.*;
import java.util.*;

public class Example {
    public static void main(String[] args) {
        StripedReadWriteLock<String> locks = StripedReadWriteLock.create(64, false);
        Map<String, Integer> balances = new HashMap<>(Map.of("A", 100, "B", 100));

        // Deadlock-free atomic transfer across multiple keys
        locks.withBulkWriteLock(List.of("B", "A"), () -> {
            balances.put("A", balances.get("A") - 25);
            balances.put("B", balances.get("B") + 25);
        });

        int balA = locks.withReadLock("A", () -> balances.get("A"));
        System.out.println("Balance A = " + balA);
    }
}
```
