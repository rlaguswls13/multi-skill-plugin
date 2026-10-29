package com.msp.ratelimit;

import java.time.Duration;

/**
 * Factory facade for creating RateLimiter instances.
 */
public final class RateLimiters {

    private RateLimiters() {
    }

    /**
     * Creates a Token Bucket rate limiter with capacity and refill rate in tokens/second.
     */
    public static RateLimiter tokenBucket(long burstCapacity, double tokensPerSecond) {
        return new TokenBucketRateLimiter(burstCapacity, tokensPerSecond);
    }

    /**
     * Creates a Token Bucket rate limiter with capacity and tokens refilled per duration period.
     */
    public static RateLimiter tokenBucket(long burstCapacity, long refillTokens, Duration refillPeriod) {
        return new TokenBucketRateLimiter(burstCapacity, refillTokens, refillPeriod);
    }

    /**
     * Creates a Sliding Window rate limiter with default 60 slices.
     */
    public static RateLimiter slidingWindow(long maxPermits, Duration window) {
        return new SlidingWindowRateLimiter(maxPermits, window);
    }

    /**
     * Creates a Sliding Window rate limiter with custom slice count.
     */
    public static RateLimiter slidingWindow(long maxPermits, Duration window, int slices) {
        return new SlidingWindowRateLimiter(maxPermits, window, slices);
    }
}
