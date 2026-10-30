package com.msp.starter.resilience;

import com.msp.cache.Caches;
import com.msp.cache.MemoryCache;
import com.msp.locker.KeyedLocker;
import com.msp.locker.KeyedLockers;
import com.msp.ratelimit.RateLimiter;
import com.msp.ratelimit.RateLimiters;
import com.msp.resilience.CircuitBreaker;
import com.msp.resilience.Resilience;
import com.msp.resilience.RetryExecutor;
import com.msp.singleflight.SingleFlight;
import com.msp.vthread.VirtualThreadRunner;

import java.time.Duration;

/**
 * 복원력, 트래픽 제어, 캐시, 락, 가상 스레드 러너 종합 스타터 파사드.
 */
public final class ResilienceConcurrencyKit {

    private ResilienceConcurrencyKit() {}

    public static RateLimiter createTokenBucketLimiter(long capacity, Duration refillPeriod) {
        return RateLimiters.tokenBucket(capacity, capacity, refillPeriod);
    }

    public static RateLimiter createSlidingWindowLimiter(long maxPermits, Duration window) {
        return RateLimiters.slidingWindow(maxPermits, window);
    }

    public static RetryExecutor createRetryExecutor(int maxAttempts, Duration initialDelay) {
        return Resilience.retry(maxAttempts, initialDelay);
    }

    public static CircuitBreaker createCircuitBreaker(String name) {
        return Resilience.circuitBreaker(name).build();
    }

    public static <K> KeyedLocker<K> createKeyedLocker() {
        return KeyedLockers.create();
    }

    public static <K, V> MemoryCache<K, V> createLruCache(int maxSize, Duration ttl) {
        return Caches.lru(maxSize, ttl);
    }

    public static <K, V> SingleFlight<K, V> createSingleFlight() {
        return SingleFlight.create();
    }

    public static VirtualThreadRunner createVirtualThreadRunner(int maxConcurrency) {
        return VirtualThreadRunner.withMaxConcurrency(maxConcurrency);
    }
}
