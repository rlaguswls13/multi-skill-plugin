package com.msp.redisscript;

/**
 * Result of a rate limit check or acquire attempt.
 *
 * @param allowed whether the request was allowed through
 * @param remainingTokens remaining requests allowed within the current window
 * @param retryAfterMillis milliseconds before the next request can be permitted (0 if allowed)
 * @param resetTimeEpochMs epoch timestamp in milliseconds when the sliding window fully clears
 */
public record RateLimitDecision(
    boolean allowed,
    long remainingTokens,
    long retryAfterMillis,
    long resetTimeEpochMs
) {
    /**
     * Returns the retry-after duration in fractional seconds.
     */
    public double retryAfterSeconds() {
        return retryAfterMillis <= 0 ? 0.0 : retryAfterMillis / 1000.0;
    }

    /**
     * Helper to create an allowed decision.
     */
    public static RateLimitDecision allow(long remaining, long resetTimeEpochMs) {
        return new RateLimitDecision(true, Math.max(0, remaining), 0, resetTimeEpochMs);
    }

    /**
     * Helper to create a rejected decision.
     */
    public static RateLimitDecision reject(long retryAfterMillis, long resetTimeEpochMs) {
        return new RateLimitDecision(false, 0, Math.max(0, retryAfterMillis), resetTimeEpochMs);
    }
}
