package com.msp.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Immutable configuration policy for retrying failed operations.
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final double multiplier;
    private final Duration maxDelay;
    private final Jitter jitter;
    private final Predicate<Throwable> retryOn;
    private final Predicate<Throwable> abortOn;

    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.initialDelay = builder.initialDelay;
        this.multiplier = builder.multiplier;
        this.maxDelay = builder.maxDelay;
        this.jitter = builder.jitter;
        this.retryOn = builder.retryOn;
        this.abortOn = builder.abortOn;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RetryPolicy defaults() {
        return builder().build();
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration initialDelay() {
        return initialDelay;
    }

    public double multiplier() {
        return multiplier;
    }

    public Duration maxDelay() {
        return maxDelay;
    }

    public Jitter jitter() {
        return jitter;
    }

    public boolean canRetry(Throwable throwable, int currentAttempt) {
        if (currentAttempt >= maxAttempts) {
            return false;
        }
        if (abortOn.test(throwable)) {
            return false;
        }
        return retryOn.test(throwable);
    }

    /**
     * Computes backoff delay for the given attempt index (1-based attempt count).
     */
    public Duration computeDelay(int attempt) {
        if (attempt <= 1) {
            return initialDelay;
        }

        double factor = Math.pow(multiplier, attempt - 1);
        double computedMillis = initialDelay.toMillis() * factor;
        long cappedMillis = Math.min((long) computedMillis, maxDelay.toMillis());

        if (cappedMillis <= 0) {
            return Duration.ZERO;
        }

        long actualMillis = switch (jitter) {
            case NONE -> cappedMillis;
            case FULL -> ThreadLocalRandom.current().nextLong(cappedMillis + 1);
            case EQUAL -> {
                long half = cappedMillis / 2;
                yield half + ThreadLocalRandom.current().nextLong(half + 1);
            }
        };

        return Duration.ofMillis(actualMillis);
    }

    public static final class Builder {
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofMillis(100);
        private double multiplier = 2.0;
        private Duration maxDelay = Duration.ofSeconds(5);
        private Jitter jitter = Jitter.NONE;
        private Predicate<Throwable> retryOn = t -> true;
        private Predicate<Throwable> abortOn = t -> false;

        private Builder() {
        }

        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be at least 1: " + maxAttempts);
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            Objects.requireNonNull(initialDelay, "initialDelay cannot be null");
            if (initialDelay.isNegative()) {
                throw new IllegalArgumentException("initialDelay cannot be negative: " + initialDelay);
            }
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder multiplier(double multiplier) {
            if (multiplier < 1.0) {
                throw new IllegalArgumentException("multiplier must be >= 1.0: " + multiplier);
            }
            this.multiplier = multiplier;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            Objects.requireNonNull(maxDelay, "maxDelay cannot be null");
            if (maxDelay.isNegative()) {
                throw new IllegalArgumentException("maxDelay cannot be negative: " + maxDelay);
            }
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder jitter(Jitter jitter) {
            this.jitter = Objects.requireNonNull(jitter, "jitter cannot be null");
            return this;
        }

        public Builder retryOn(Predicate<Throwable> predicate) {
            this.retryOn = Objects.requireNonNull(predicate, "predicate cannot be null");
            return this;
        }

        public Builder retryOn(Class<? extends Throwable> exceptionClass) {
            Objects.requireNonNull(exceptionClass, "exceptionClass cannot be null");
            this.retryOn = exceptionClass::isInstance;
            return this;
        }

        public Builder abortOn(Predicate<Throwable> predicate) {
            this.abortOn = Objects.requireNonNull(predicate, "predicate cannot be null");
            return this;
        }

        public Builder abortOn(Class<? extends Throwable> exceptionClass) {
            Objects.requireNonNull(exceptionClass, "exceptionClass cannot be null");
            this.abortOn = exceptionClass::isInstance;
            return this;
        }

        public RetryPolicy build() {
            if (initialDelay.compareTo(maxDelay) > 0) {
                this.maxDelay = initialDelay;
            }
            return new RetryPolicy(this);
        }
    }
}
