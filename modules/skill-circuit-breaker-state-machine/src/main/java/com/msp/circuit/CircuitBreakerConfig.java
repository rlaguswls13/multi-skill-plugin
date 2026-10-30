package com.msp.circuit;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for {@link CircuitBreakerStateMachine}.
 */
public final class CircuitBreakerConfig {

    private final float failureRateThreshold;
    private final int slidingWindowSize;
    private final Duration waitDurationInOpen;
    private final int permittedNumberOfCallsInHalfOpenState;
    private final int minimumNumberOfCalls;

    public CircuitBreakerConfig(
            float failureRateThreshold,
            int slidingWindowSize,
            Duration waitDurationInOpen,
            int permittedNumberOfCallsInHalfOpenState,
            int minimumNumberOfCalls
    ) {
        if (failureRateThreshold <= 0.0f || failureRateThreshold > 100.0f) {
            throw new IllegalArgumentException("failureRateThreshold must be between (0, 100], got: " + failureRateThreshold);
        }
        if (slidingWindowSize <= 0) {
            throw new IllegalArgumentException("slidingWindowSize must be positive, got: " + slidingWindowSize);
        }
        Objects.requireNonNull(waitDurationInOpen, "waitDurationInOpen must not be null");
        if (waitDurationInOpen.isNegative() || waitDurationInOpen.isZero()) {
            throw new IllegalArgumentException("waitDurationInOpen must be positive, got: " + waitDurationInOpen);
        }
        if (permittedNumberOfCallsInHalfOpenState <= 0) {
            throw new IllegalArgumentException("permittedNumberOfCallsInHalfOpenState must be positive, got: " + permittedNumberOfCallsInHalfOpenState);
        }
        if (minimumNumberOfCalls <= 0 || minimumNumberOfCalls > slidingWindowSize) {
            throw new IllegalArgumentException("minimumNumberOfCalls must be between 1 and slidingWindowSize (" + slidingWindowSize + ")");
        }

        this.failureRateThreshold = failureRateThreshold;
        this.slidingWindowSize = slidingWindowSize;
        this.waitDurationInOpen = waitDurationInOpen;
        this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        this.minimumNumberOfCalls = minimumNumberOfCalls;
    }

    public static CircuitBreakerConfig ofDefaults() {
        return custom().build();
    }

    public static Builder custom() {
        return new Builder();
    }

    public float failureRateThreshold() {
        return failureRateThreshold;
    }

    public int slidingWindowSize() {
        return slidingWindowSize;
    }

    public Duration waitDurationInOpen() {
        return waitDurationInOpen;
    }

    public int permittedNumberOfCallsInHalfOpenState() {
        return permittedNumberOfCallsInHalfOpenState;
    }

    public int minimumNumberOfCalls() {
        return minimumNumberOfCalls;
    }

    public static final class Builder {
        private float failureRateThreshold = 50.0f; // 50%
        private int slidingWindowSize = 10;
        private Duration waitDurationInOpen = Duration.ofSeconds(5);
        private int permittedNumberOfCallsInHalfOpenState = 3;
        private Integer minimumNumberOfCalls = null;

        public Builder failureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
            return this;
        }

        public Builder slidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
            return this;
        }

        public Builder waitDurationInOpen(Duration waitDurationInOpen) {
            this.waitDurationInOpen = waitDurationInOpen;
            return this;
        }

        public Builder permittedNumberOfCallsInHalfOpenState(int permittedNumberOfCalls) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCalls;
            return this;
        }

        public Builder minimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
            return this;
        }

        public CircuitBreakerConfig build() {
            int minCalls = (minimumNumberOfCalls != null) ? minimumNumberOfCalls : slidingWindowSize;
            return new CircuitBreakerConfig(
                    failureRateThreshold,
                    slidingWindowSize,
                    waitDurationInOpen,
                    permittedNumberOfCallsInHalfOpenState,
                    minCalls
            );
        }
    }
}
