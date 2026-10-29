package com.msp.cache;

/**
 * Immutable snapshot of cache performance metrics.
 */
public record CacheStats(
        long hitCount,
        long missCount,
        long evictionCount,
        long expirationCount
) {
    public long requestCount() {
        return hitCount + missCount;
    }

    public double hitRate() {
        long total = requestCount();
        return total == 0 ? 0.0 : (double) hitCount / (double) total;
    }

    public double missRate() {
        long total = requestCount();
        return total == 0 ? 0.0 : (double) missCount / (double) total;
    }

    public static CacheStats empty() {
        return new CacheStats(0, 0, 0, 0);
    }
}
