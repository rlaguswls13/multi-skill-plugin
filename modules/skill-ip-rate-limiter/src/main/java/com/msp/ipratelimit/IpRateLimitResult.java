package com.msp.ipratelimit;

import java.time.Duration;

public record IpRateLimitResult(
        boolean allowed,
        long remainingPermits,
        Duration retryAfter
) {
    public static IpRateLimitResult allowed(long remaining) {
        return new IpRateLimitResult(true, remaining, Duration.ZERO);
    }

    public static IpRateLimitResult rejected(Duration retryAfter) {
        return new IpRateLimitResult(false, 0, retryAfter);
    }
}
