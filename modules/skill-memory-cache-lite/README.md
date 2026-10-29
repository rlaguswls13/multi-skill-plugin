# ⚡ skill-memory-cache-lite

> **High-Performance In-Memory Concurrent LRU Cache with TTL for Java 21**  
> Provides Thread-Safe bounded capacity eviction (LRU), default/per-entry TTL expiration, atomic `computeIfAbsent`, and detailed hit/miss metrics.

---

## 🚀 Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library (`ConcurrentHashMap`, `ReentrantLock`).
- **Concurrent LRU Eviction**:
  - Thread-safe doubly-linked list ordering.
  - Automatically evicts least recently used items when `maxCapacity` is reached.
  - Reading or updating an entry refreshes its recency.
- **Flexible Time-To-Live (TTL)**:
  - Cache-wide default TTL and per-entry custom TTL.
  - Lazy expiration on access + proactive sweep via `cleanUpExpired()`.
- **Atomic Operations**:
  - Thread-safe `computeIfAbsent(key, ttl, mappingFunction)`.
- **Rich Performance Metrics**:
  - `CacheStats` tracking `hitCount`, `missCount`, `evictionCount`, `expirationCount`, `hitRate`, and `missRate`.
- **Unified Factory**: `Caches` facade and builder.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-memory-cache-lite:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. LRU Cache with TTL

```java
import com.msp.cache.MemoryCache;
import com.msp.cache.Caches;
import java.time.Duration;

// Cache up to 500 items, expiring after 10 minutes by default
MemoryCache<String, UserProfile> cache = Caches.lru(500, Duration.ofMinutes(10));

// Basic put and get
cache.put("user-1", profile);
UserProfile user = cache.get("user-1").orElse(null);

// Per-entry custom TTL
cache.put("session-token", token, Duration.ofSeconds(30));
```

### 2. Atomic Cache-Aside with `computeIfAbsent`

```java
// If absent, fetches from DB, caches with 5-minute TTL, and returns value
UserProfile profile = cache.computeIfAbsent(
    "user-100",
    Duration.ofMinutes(5),
    userId -> database.findUserById(userId)
);
```

### 3. Monitoring Metrics

```java
CacheStats stats = cache.stats();
System.out.printf("Hit rate: %.2f%% (Hits: %d, Misses: %d)%n",
        stats.hitRate() * 100,
        stats.hitCount(),
        stats.missCount());
```
