# skill-concurrent-rate-limiter-distributed-redis-script

> Pure Java sliding window rate limiter logic emulator and canonical Redis Lua script exporter for distributed rate limiting using sorted sets.

---

## 🚀 Features

- **Canonical Redis Lua Script**:
  - Implements sliding window rate limiting via Redis Sorted Sets (`ZREMRANGEBYSCORE`, `ZCARD`, `ZADD`, `PEXPIRE`, `ZRANGE`).
  - Returns `[allowed, remaining_tokens, retry_after_ms, reset_time_epoch_ms]`.
  - Computes precomputed SHA-1 hash for high-throughput `EVALSHA` execution.
- **Pure Java In-Memory Emulator**:
  - `RedisSlidingWindowRateLimiterEmulator` faithfully simulates the exact Redis sorted-set semantics in local Java memory.
  - Thread-safe and lock-optimized per key (`ConcurrentHashMap` + localized synchronization).
  - Perfect for unit testing, offline development, and standalone applications without running a real Redis server.
- **Rich Observability**:
  - `RateLimitDecision` provides boolean allowance, remaining quota, retry-after in milliseconds & seconds, and window expiration epoch timestamp.
- **Pure Java 21**: Zero 3rd-party dependencies.

---

## 📦 Usage Examples

### 1. In-Memory Local Emulator

```java
import com.msp.redisscript.*;
import java.time.Duration;

RedisSlidingWindowRateLimiterEmulator limiter = new RedisSlidingWindowRateLimiterEmulator();

// Allow 5 requests per 10 seconds for user "alice"
RateLimitDecision decision = limiter.tryAcquire("rate:user:alice", 5, Duration.ofSeconds(10));

if (decision.allowed()) {
    System.out.println("Allowed! Tokens remaining: " + decision.remainingTokens());
} else {
    System.out.println("Rate limit exceeded! Retry after: " + decision.retryAfterSeconds() + "s");
}
```

### 2. Export Redis Lua Script for Jedis / Lettuce / Spring Data Redis

```java
import com.msp.redisscript.RedisRateLimiterScript;

// 1. Get raw Lua script
String script = RedisRateLimiterScript.getLuaScript();

// 2. Pre-calculated SHA1 for EVALSHA
String sha1 = RedisRateLimiterScript.getSha1();

// 3. Format EVAL argument list
long nowMs = System.currentTimeMillis();
long windowMs = 10_000L;
long limit = 10L;
List<String> args = RedisRateLimiterScript.buildArgs(nowMs, windowMs, limit, "req-uuid-1");

// 4. Parse Redis response array [allowed, remaining, retryAfterMs, resetMs]
RateLimitDecision decision = RedisRateLimiterScript.parseResponse(redisEvalResult);
```

---

## 🧪 Testing

```bash
./gradlew :modules:skill-concurrent-rate-limiter-distributed-redis-script:test
```
