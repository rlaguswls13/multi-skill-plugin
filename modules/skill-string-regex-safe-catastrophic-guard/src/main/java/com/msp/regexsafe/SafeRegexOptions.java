package com.msp.regexsafe;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration options for safe regular expression evaluation.
 *
 * @param timeout maximum execution duration allowed for a single match evaluation
 * @param maxSteps maximum character inspection steps allowed before aborting
 * @param enableStaticPreCheck whether to statically inspect pattern for nested quantifiers
 * @param throwOnFailure whether to throw exceptions instead of returning error result records
 */
public record SafeRegexOptions(
        Duration timeout,
        long maxSteps,
        boolean enableStaticPreCheck,
        boolean throwOnFailure
) {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(500);
    public static final long DEFAULT_MAX_STEPS = 50_000L;

    public SafeRegexOptions {
        Objects.requireNonNull(timeout, "timeout cannot be null");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps must be positive");
        }
    }

    public static SafeRegexOptions defaults() {
        return new SafeRegexOptions(DEFAULT_TIMEOUT, DEFAULT_MAX_STEPS, true, false);
    }

    public static SafeRegexOptions ofTimeout(Duration timeout) {
        return new SafeRegexOptions(timeout, DEFAULT_MAX_STEPS, true, false);
    }

    public SafeRegexOptions withMaxSteps(long maxSteps) {
        return new SafeRegexOptions(this.timeout, maxSteps, this.enableStaticPreCheck, this.throwOnFailure);
    }

    public SafeRegexOptions withTimeout(Duration timeout) {
        return new SafeRegexOptions(timeout, this.maxSteps, this.enableStaticPreCheck, this.throwOnFailure);
    }

    public SafeRegexOptions withThrowOnFailure(boolean throwOnFailure) {
        return new SafeRegexOptions(this.timeout, this.maxSteps, this.enableStaticPreCheck, throwOnFailure);
    }

    public SafeRegexOptions withStaticPreCheck(boolean enable) {
        return new SafeRegexOptions(this.timeout, this.maxSteps, enable, this.throwOnFailure);
    }
}
