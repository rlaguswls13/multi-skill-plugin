package com.msp.backoff;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Enterprise-grade Exponential Backoff and Jitter calculator.
 * <p>
 * Implements AWS Architecture best practices (Marc Brooker) to avoid Thundering Herd problems
 * during downstream outages and rate limiting events.
 */
public final class BackoffCalculator {

    public static final double DEFAULT_MULTIPLIER = 2.0;

    private BackoffCalculator() {
        // Utility class
    }

    /**
     * Calculates delay using default multiplier (2.0) and FULL_JITTER strategy.
     */
    public static Duration calculateDelay(int attempt, Duration initialDelay, Duration maxDelay) {
        return calculateDelay(attempt, initialDelay, maxDelay, DEFAULT_MULTIPLIER, JitterStrategy.FULL_JITTER);
    }

    /**
     * Calculates delay using default multiplier (2.0) and specified jitter strategy.
     */
    public static Duration calculateDelay(int attempt, Duration initialDelay, Duration maxDelay, JitterStrategy strategy) {
        return calculateDelay(attempt, initialDelay, maxDelay, DEFAULT_MULTIPLIER, strategy);
    }

    /**
     * Calculates delay using specified multiplier, strategy, and ThreadLocalRandom.
     */
    public static Duration calculateDelay(
            int attempt,
            Duration initialDelay,
            Duration maxDelay,
            double multiplier,
            JitterStrategy strategy
    ) {
        return calculateDelay(attempt, initialDelay, maxDelay, multiplier, strategy, ThreadLocalRandom.current());
    }

    /**
     * Calculates exponential backoff delay with specified parameters and custom random generator.
     *
     * @param attempt      zero-based or positive retry attempt index (0 is first retry)
     * @param initialDelay baseline delay for attempt 0
     * @param maxDelay     upper ceiling cap for the delay
     * @param multiplier   exponential base factor (must be &gt;= 1.0)
     * @param strategy     {@link JitterStrategy} algorithm to apply
     * @param random       {@link RandomGenerator} for jitter calculation
     * @return calculated {@link Duration}
     */
    public static Duration calculateDelay(
            int attempt,
            Duration initialDelay,
            Duration maxDelay,
            double multiplier,
            JitterStrategy strategy,
            RandomGenerator random
    ) {
        Objects.requireNonNull(initialDelay, "initialDelay must not be null");
        Objects.requireNonNull(maxDelay, "maxDelay must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        Objects.requireNonNull(random, "random must not be null");

        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0: " + multiplier);
        }

        long baseMillis = Math.max(0L, initialDelay.toMillis());
        long maxMillis = Math.max(baseMillis, maxDelay.toMillis());

        int safeAttempt = Math.max(0, attempt);

        // Exponential backoff ceiling: min(maxDelay, base * multiplier^attempt)
        double exponential = baseMillis * Math.pow(multiplier, safeAttempt);
        long temp = (Double.isInfinite(exponential) || exponential >= maxMillis)
                ? maxMillis
                : Math.max(baseMillis, Math.round(exponential));

        long delayMillis = switch (strategy) {
            case NONE -> temp;
            case FULL_JITTER -> (temp <= 0) ? 0 : random.nextLong(0, temp + 1);
            case EQUAL_JITTER -> {
                long half = temp / 2;
                long randPart = (half <= 0) ? 0 : random.nextLong(0, half + 1);
                yield half + randPart;
            }
            case DECORRELATED_JITTER -> {
                // If attempt == 0, base with minor jitter; otherwise randomize between base and temp
                if (temp <= baseMillis) {
                    yield baseMillis;
                }
                yield random.nextLong(baseMillis, temp + 1);
            }
        };

        return Duration.ofMillis(Math.min(maxMillis, Math.max(0L, delayMillis)));
    }

    /**
     * Calculates decorrelated jitter delay based on the previous attempt's delay:
     * {@code sleep = min(maxDelay, rand(initialDelay, previousDelay * 3))}.
     *
     * @param previousDelay delay from preceding retry attempt
     * @param initialDelay  baseline delay
     * @param maxDelay      upper ceiling
     * @return next delay {@link Duration}
     */
    public static Duration calculateDecorrelatedDelay(
            Duration previousDelay,
            Duration initialDelay,
            Duration maxDelay
    ) {
        return calculateDecorrelatedDelay(previousDelay, initialDelay, maxDelay, ThreadLocalRandom.current());
    }

    /**
     * Calculates decorrelated jitter delay with custom random generator.
     */
    public static Duration calculateDecorrelatedDelay(
            Duration previousDelay,
            Duration initialDelay,
            Duration maxDelay,
            RandomGenerator random
    ) {
        Objects.requireNonNull(initialDelay, "initialDelay must not be null");
        Objects.requireNonNull(maxDelay, "maxDelay must not be null");
        Objects.requireNonNull(random, "random must not be null");

        long baseMillis = Math.max(0L, initialDelay.toMillis());
        long maxMillis = Math.max(baseMillis, maxDelay.toMillis());
        long prevMillis = (previousDelay == null) ? baseMillis : Math.max(baseMillis, previousDelay.toMillis());

        long upper = Math.min(maxMillis, prevMillis * 3);
        long nextMillis = (upper <= baseMillis)
                ? baseMillis
                : random.nextLong(baseMillis, upper + 1);

        return Duration.ofMillis(Math.min(maxMillis, nextMillis));
    }
}
