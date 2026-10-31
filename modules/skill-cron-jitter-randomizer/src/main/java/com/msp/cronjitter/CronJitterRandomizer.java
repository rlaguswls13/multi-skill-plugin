package com.msp.cronjitter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Multi-strategy jitter engine to prevent Thundering Herd (stampede) on periodic batch/cron execution
 * and retry backoff.
 */
public final class CronJitterRandomizer {

    private final Random customRandom;

    /**
     * Creates a CronJitterRandomizer backed by {@link ThreadLocalRandom}.
     */
    public CronJitterRandomizer() {
        this.customRandom = null;
    }

    /**
     * Creates a CronJitterRandomizer with a deterministic custom {@link Random} provider
     * for repeatable testing.
     */
    public CronJitterRandomizer(Random random) {
        this.customRandom = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Creates a CronJitterRandomizer with a fixed seed.
     */
    public CronJitterRandomizer(long seed) {
        this.customRandom = new Random(seed);
    }

    private static final CronJitterRandomizer DEFAULT = new CronJitterRandomizer();

    /**
     * Static shortcut using {@link ThreadLocalRandom}.
     */
    public static Instant applyJitter(Instant scheduledTime, Duration maxJitter, JitterStrategy strategy) {
        return DEFAULT.apply(scheduledTime, maxJitter, strategy);
    }

    /**
     * Static shortcut using {@link ThreadLocalRandom}.
     */
    public static Duration computeBackoffJitter(Duration baseInterval, Duration maxInterval, int retryAttempt, JitterStrategy strategy) {
        return DEFAULT.computeBackoff(baseInterval, maxInterval, retryAttempt, strategy);
    }

    /**
     * Applies jitter to a scheduled time according to the selected strategy.
     *
     * @param scheduledTime the original scheduled execution time
     * @param maxJitter the maximum jitter duration
     * @param strategy the jitter strategy (FULL, EQUAL, DECORRELATED)
     * @return the jittered instant
     */
    public Instant apply(Instant scheduledTime, Duration maxJitter, JitterStrategy strategy) {
        Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
        Objects.requireNonNull(maxJitter, "maxJitter must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");

        long maxMillis = maxJitter.toMillis();
        if (maxMillis <= 0) {
            return scheduledTime;
        }

        long jitterMillis = switch (strategy) {
            case FULL -> randomBetween(0, maxMillis + 1);
            case EQUAL -> {
                long half = maxMillis / 2;
                yield half + randomBetween(0, (maxMillis - half) + 1);
            }
            case DECORRELATED -> {
                long min = maxMillis / 3;
                yield randomBetween(min, maxMillis + 1);
            }
        };

        return scheduledTime.plusMillis(jitterMillis);
    }

    /**
     * Computes backoff duration with jitter for retry attempts according to the selected strategy.
     *
     * @param baseInterval the base backoff interval (attempt 0)
     * @param maxInterval the maximum allowed interval cap
     * @param retryAttempt zero-indexed retry attempt
     * @param strategy the jitter strategy
     * @return the calculated backoff duration
     */
    public Duration computeBackoff(Duration baseInterval, Duration maxInterval, int retryAttempt, JitterStrategy strategy) {
        Objects.requireNonNull(baseInterval, "baseInterval must not be null");
        Objects.requireNonNull(maxInterval, "maxInterval must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");

        long baseMs = Math.max(1, baseInterval.toMillis());
        long maxMs = Math.max(baseMs, maxInterval.toMillis());
        int attempt = Math.max(0, retryAttempt);

        long resultMs;
        switch (strategy) {
            case FULL -> {
                long expCap = calculateExponentialCap(baseMs, maxMs, attempt);
                resultMs = randomBetween(0, expCap + 1);
            }
            case EQUAL -> {
                long expCap = calculateExponentialCap(baseMs, maxMs, attempt);
                long half = expCap / 2;
                resultMs = half + randomBetween(0, (expCap - half) + 1);
            }
            case DECORRELATED -> {
                long sleep = baseMs;
                for (int i = 0; i < attempt; i++) {
                    long upper = Math.min(maxMs, sleep * 3);
                    if (upper <= baseMs) {
                        sleep = baseMs;
                    } else {
                        sleep = randomBetween(baseMs, upper + 1);
                    }
                }
                resultMs = Math.min(maxMs, Math.max(baseMs, sleep));
            }
            default -> throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }

        return Duration.ofMillis(resultMs);
    }

    private static long calculateExponentialCap(long baseMs, long maxMs, int attempt) {
        if (attempt >= 62) {
            return maxMs;
        }
        long multiplier = 1L << attempt;
        if (baseMs > maxMs / multiplier) {
            return maxMs;
        }
        return Math.min(maxMs, baseMs * multiplier);
    }

    private long randomBetween(long minInclusive, long maxExclusive) {
        if (maxExclusive <= minInclusive) {
            return minInclusive;
        }
        long bound = maxExclusive - minInclusive;
        if (customRandom != null) {
            return minInclusive + customRandom.nextLong(bound);
        } else {
            return ThreadLocalRandom.current().nextLong(minInclusive, maxExclusive);
        }
    }
}
