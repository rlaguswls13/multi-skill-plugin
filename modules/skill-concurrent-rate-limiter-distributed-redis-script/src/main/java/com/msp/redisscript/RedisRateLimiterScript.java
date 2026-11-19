package com.msp.redisscript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Exports canonical, battle-tested Redis Lua Script for sliding window rate limiting using sorted sets.
 */
public final class RedisRateLimiterScript {

    /**
     * Canonical Redis Lua script using sorted sets (ZREMRANGEBYSCORE, ZCARD, ZADD, PEXPIRE, ZRANGE).
     *
     * KEYS[1]: Rate limit identifier (e.g. "rate:user:42")
     * ARGV[1]: Current timestamp in epoch milliseconds
     * ARGV[2]: Window size in milliseconds
     * ARGV[3]: Max requests allowed in window (limit)
     * ARGV[4]: Unique request ID / member (e.g. UUID, timestamp-counter)
     *
     * Returns: {allowed (1 or 0), remaining_tokens, retry_after_ms, reset_time_epoch_ms}
     */
    public static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local window = tonumber(ARGV[2])
        local limit = tonumber(ARGV[3])
        local member = ARGV[4]
        
        local clear_before = now - window
        redis.call('ZREMRANGEBYSCORE', key, '-inf', clear_before)
        local current_requests = redis.call('ZCARD', key)
        
        if current_requests < limit then
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            local remaining = limit - current_requests - 1
            return {1, remaining, 0, now + window}
        else
            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            local retry_after = 0
            local reset_time = now + window
            if #oldest >= 2 then
                local oldest_ts = tonumber(oldest[2])
                reset_time = oldest_ts + window
                if reset_time > now then
                    retry_after = reset_time - now
                end
            end
            return {0, 0, retry_after, reset_time}
        end
        """.stripIndent().trim();

    private static final String SHA1 = computeSha1(LUA_SCRIPT);

    private RedisRateLimiterScript() {}

    /**
     * Returns the canonical Redis Lua script string.
     */
    public static String getLuaScript() {
        return LUA_SCRIPT;
    }

    /**
     * Returns the SHA-1 digest of the script for Redis EVALSHA commands.
     */
    public static String getSha1() {
        return SHA1;
    }

    /**
     * Formats arguments for standard Redis EVAL / EVALSHA calls.
     *
     * @param nowMs current epoch milliseconds
     * @param windowMs sliding window duration in milliseconds
     * @param limit max allowed requests in window
     * @param memberId unique member id (e.g. UUID)
     * @return list of string arguments for ARGV[1..4]
     */
    public static List<String> buildArgs(long nowMs, long windowMs, long limit, String memberId) {
        return List.of(
            String.valueOf(nowMs),
            String.valueOf(windowMs),
            String.valueOf(limit),
            memberId != null ? memberId : (nowMs + "-" + System.nanoTime())
        );
    }

    /**
     * Parses the Redis script response array: {allowed, remaining, retry_after_ms, reset_time_epoch_ms}.
     */
    public static RateLimitDecision parseResponse(List<?> result) {
        if (result == null || result.size() < 4) {
            throw new IllegalArgumentException("Invalid Redis script response: " + result);
        }
        long allowed = toLong(result.get(0));
        long remaining = toLong(result.get(1));
        long retryAfterMs = toLong(result.get(2));
        long resetTimeMs = toLong(result.get(3));

        return allowed == 1
            ? RateLimitDecision.allow(remaining, resetTimeMs)
            : RateLimitDecision.reject(retryAfterMs, resetTimeMs);
    }

    private static long toLong(Object obj) {
        if (obj instanceof Number num) {
            return num.longValue();
        }
        return Long.parseLong(String.valueOf(obj));
    }

    private static String computeSha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 digest not available", e);
        }
    }
}
