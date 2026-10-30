package com.msp.starter.resilience;

import com.msp.cache.MemoryCache;
import com.msp.locker.KeyedLocker;
import com.msp.ratelimit.RateLimiter;
import com.msp.resilience.CircuitBreaker;
import com.msp.resilience.RetryExecutor;
import com.msp.singleflight.SingleFlight;
import com.msp.vthread.VirtualThreadRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResilienceConcurrencyKitTest {

    @Test
    @DisplayName("Integrated resilience kit facade test")
    void testResilienceKit() throws Exception {
        RateLimiter limiter = ResilienceConcurrencyKit.createTokenBucketLimiter(5, Duration.ofSeconds(1));
        assertTrue(limiter.tryAcquire());

        RetryExecutor retry = ResilienceConcurrencyKit.createRetryExecutor(2, Duration.ofMillis(10));
        String val = retry.execute(() -> "success");
        assertEquals("success", val);

        CircuitBreaker cb = ResilienceConcurrencyKit.createCircuitBreaker("test-cb");
        assertEquals("cb-ok", cb.execute(() -> "cb-ok"));

        KeyedLocker<String> locker = ResilienceConcurrencyKit.createKeyedLocker();
        String lockedResult = locker.withLock("user:101", () -> "locked");
        assertEquals("locked", lockedResult);

        MemoryCache<String, String> cache = ResilienceConcurrencyKit.createLruCache(10, Duration.ofMinutes(1));
        cache.put("k1", "v1");
        assertEquals("v1", cache.get("k1").orElse(null));

        SingleFlight<String, String> sf = ResilienceConcurrencyKit.createSingleFlight();
        AtomicInteger count = new AtomicInteger();
        String res = sf.execute("key", () -> {
            count.incrementAndGet();
            return "data";
        });
        assertEquals("data", res);
        assertEquals(1, count.get());

        try (VirtualThreadRunner runner = ResilienceConcurrencyKit.createVirtualThreadRunner(4)) {
            var future = runner.submit(() -> "vthread-ok");
            assertEquals("vthread-ok", future.get());
        }
    }
}
