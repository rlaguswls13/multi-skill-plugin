# 🌸 skill-bloom-filter-counting

> **Counting Bloom Filter supporting addition, deletion, and approximate frequency estimation**  
> Pure Java 21 zero-dependency Counting Bloom Filter using 8-bit counters to support dynamic item removals and frequency tracking.

---

## 🚀 Key Features

- **Supports Deletion (`remove`)**: Unlike standard standard Bloom filters where bits cannot be turned off, bucket counters decrement when items are removed.
- **8-Bit Counter Arrays**: Each bucket uses an 8-bit unsigned counter (0..255) to prevent early saturation.
- **Approximate Frequency Estimation**: `approximateCount(element)` inspects the minimum counter value across the element's hash indices.
- **Optimal Sizing Calculator**: `CountingBloomFilter.create(n, fpp)` automatically calculates capacity $m$ and hash count $k$ based on expected item volume and error rate.
- **Thread-Safe**: Uses `ReentrantReadWriteLock` for high-concurrency read operations (`mightContain`, `approximateCount`) and thread-safe writes (`add`, `remove`, `clear`).

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-bloom-filter-counting:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Basic Add, Query, and Remove

```java
import com.msp.cbloom.CountingBloomFilter;

CountingBloomFilter filter = CountingBloomFilter.create(10_000, 0.01);

// Add items
filter.add("user:42");
filter.add("user:42");

// Query presence
if (filter.mightContain("user:42")) {
    System.out.println("User is present! Approximate count: " + filter.approximateCount("user:42"));
}

// Remove item
boolean removed = filter.remove("user:42");
System.out.println("Removed once: " + removed); // true
System.out.println("Approximate count after 1 remove: " + filter.approximateCount("user:42")); // 1

filter.remove("user:42");
System.out.println("Might contain after 2nd remove: " + filter.mightContain("user:42")); // false
```

### 2. Stream De-duplication and Eviction

```java
CountingBloomFilter activeWindowFilter = new CountingBloomFilter(4096, 4);

// Slide-in item
activeWindowFilter.add("event_123");

// Later when sliding window rolls past:
activeWindowFilter.remove("event_123");
```
