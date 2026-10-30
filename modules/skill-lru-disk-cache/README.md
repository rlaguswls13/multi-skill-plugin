# 🗄️ skill-lru-disk-cache

> **File-system based LRU and TTL disk cache manager without external dependencies**  
> Pure Java 21 file-backed key-value cache with automatic TTL expiration, atomic writes, and LRU capacity eviction.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Standard Java 21 `java.nio.file` and `java.nio.channels` only.
- **Atomic Disk Writes**: Writes to a temp file and performs atomic replace (`StandardCopyOption.ATOMIC_MOVE`) to prevent partial corruptions.
- **Self-Contained Binary Format**: Header stores magic bytes, version, expiration timestamp, last access time, and original key.
- **LRU Eviction**: Automatically purges oldest accessed files when the directory size exceeds the configured quota (`evictIfExceeds`).
- **TTL Expiration**: Expired keys are automatically deleted on read or by calling `cleanExpired()`.
- **Thread-Safe**: Guarded by `ReentrantReadWriteLock` for concurrent access.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-lru-disk-cache:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Basic Put and Get with TTL

```java
import com.msp.diskcache.LruDiskCache;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

Path cacheDir = Path.of("./data/cache");
try (LruDiskCache cache = new LruDiskCache(cacheDir)) {
    // Put entry valid for 30 minutes
    cache.put("user:profile:100", "{\"name\": \"Alice\"}", Duration.ofMinutes(30));

    // Retrieve entry (automatically updates last accessed timestamp)
    Optional<String> profile = cache.getString("user:profile:100");
    profile.ifPresent(System.out::println);
}
```

### 2. Disk Quota Management (LRU Eviction)

```java
// Evict oldest accessed entries if total directory size exceeds 500 MB
long maxBytes = 500L * 1024 * 1024;
int evictedFiles = cache.evictIfExceeds(maxBytes);
System.out.println("Evicted " + evictedFiles + " files to enforce quota.");
```

### 3. Periodic Expiration Cleanup

```java
// Purge all expired files
int purgedCount = cache.cleanExpired();
System.out.println("Purged " + purgedCount + " expired entries.");
```
