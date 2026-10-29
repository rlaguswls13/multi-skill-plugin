package com.msp.resilience;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Thread-safe Circuit Breaker implementing CLOSED, OPEN, and HALF_OPEN states
 * with sliding window metrics and configurable failure threshold.
 */
public final class CircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public record Stats(
            State state,
            int totalCalls,
            int failedCalls,
            double failureRate,
            int consecutiveSuccesses
    ) {}

    private final String name;
    private final double failureRateThreshold;
    private final int minimumCalls;
    private final Duration waitDurationInOpen;
    private final int permittedCallsInHalfOpen;
    private final int slidingWindowSize;
    private final Predicate<Throwable> recordException;

    private final ReentrantLock lock = new ReentrantLock();

    private State state = State.CLOSED;
    private long openStateTimestampNano = 0L;

    // Sliding window buffer: 1 = success, 0 = failure, -1 = empty
    private final byte[] window;
    private int windowIndex = 0;
    private int windowCount = 0;

    // Half-open state tracking
    private int halfOpenCallsAllowed = 0;
    private int halfOpenSuccessCount = 0;

    private CircuitBreaker(Builder builder) {
        this.name = builder.name;
        this.failureRateThreshold = builder.failureRateThreshold;
        this.minimumCalls = builder.minimumCalls;
        this.waitDurationInOpen = builder.waitDurationInOpen;
        this.permittedCallsInHalfOpen = builder.permittedCallsInHalfOpen;
        this.slidingWindowSize = builder.slidingWindowSize;
        this.recordException = builder.recordException;

        this.window = new byte[slidingWindowSize];
        Arrays.fill(this.window, (byte) -1);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public State getState() {
        lock.lock();
        try {
            checkOpenToHalfOpenTransition();
            return state;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Attempts to acquire permission to execute an operation.
     */
    public boolean tryAcquirePermission() {
        lock.lock();
        try {
            checkOpenToHalfOpenTransition();

            return switch (state) {
                case CLOSED -> true;
                case OPEN -> false;
                case HALF_OPEN -> {
                    if (halfOpenCallsAllowed < permittedCallsInHalfOpen) {
                        halfOpenCallsAllowed++;
                        yield true;
                    }
                    yield false;
                }
            };
        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes the supplier protected by the circuit breaker.
     *
     * @throws CircuitBreakerOpenException if the circuit breaker is OPEN
     */
    public <T> T execute(Supplier<T> action) {
        Objects.requireNonNull(action, "action cannot be null");

        if (!tryAcquirePermission()) {
            throw new CircuitBreakerOpenException(name);
        }

        try {
            T result = action.get();
            recordSuccess();
            return result;
        } catch (Throwable t) {
            if (recordException.test(t)) {
                recordFailure(t);
            } else {
                recordSuccess();
            }
            if (t instanceof RuntimeException re) {
                throw re;
            } else if (t instanceof Error e) {
                throw e;
            } else {
                throw new RuntimeException(t);
            }
        }
    }

    /**
     * Runs the runnable protected by the circuit breaker.
     */
    public void run(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    public void recordSuccess() {
        lock.lock();
        try {
            checkOpenToHalfOpenTransition();

            if (state == State.HALF_OPEN) {
                halfOpenSuccessCount++;
                if (halfOpenSuccessCount >= permittedCallsInHalfOpen) {
                    transitionToClosedInternal();
                }
            } else if (state == State.CLOSED) {
                recordCallOutcome((byte) 1);
            }
        } finally {
            lock.unlock();
        }
    }

    public void recordFailure(Throwable throwable) {
        lock.lock();
        try {
            checkOpenToHalfOpenTransition();

            if (state == State.HALF_OPEN) {
                // Any recorded failure in HALF_OPEN immediately trips back to OPEN
                transitionToOpenInternal();
            } else if (state == State.CLOSED) {
                recordCallOutcome((byte) 0);
                evaluateClosedFailureRate();
            }
        } finally {
            lock.unlock();
        }
    }

    public void reset() {
        lock.lock();
        try {
            transitionToClosedInternal();
        } finally {
            lock.unlock();
        }
    }

    public void transitionToOpen() {
        lock.lock();
        try {
            transitionToOpenInternal();
        } finally {
            lock.unlock();
        }
    }

    public void transitionToClosed() {
        lock.lock();
        try {
            transitionToClosedInternal();
        } finally {
            lock.unlock();
        }
    }

    public void transitionToHalfOpen() {
        lock.lock();
        try {
            transitionToHalfOpenInternal();
        } finally {
            lock.unlock();
        }
    }

    public Stats getStats() {
        lock.lock();
        try {
            checkOpenToHalfOpenTransition();
            int total = 0;
            int failed = 0;
            for (byte b : window) {
                if (b != -1) {
                    total++;
                    if (b == 0) {
                        failed++;
                    }
                }
            }
            double rate = total == 0 ? 0.0 : (failed * 100.0) / total;
            return new Stats(state, total, failed, rate, halfOpenSuccessCount);
        } finally {
            lock.unlock();
        }
    }

    private void checkOpenToHalfOpenTransition() {
        if (state == State.OPEN) {
            long elapsedNano = System.nanoTime() - openStateTimestampNano;
            if (elapsedNano >= waitDurationInOpen.toNanos()) {
                transitionToHalfOpenInternal();
            }
        }
    }

    private void recordCallOutcome(byte outcome) {
        window[windowIndex] = outcome;
        windowIndex = (windowIndex + 1) % slidingWindowSize;
        if (windowCount < slidingWindowSize) {
            windowCount++;
        }
    }

    private void evaluateClosedFailureRate() {
        if (windowCount < minimumCalls) {
            return;
        }

        int failed = 0;
        for (int i = 0; i < windowCount; i++) {
            if (window[i] == 0) {
                failed++;
            }
        }

        double rate = (failed * 100.0) / windowCount;
        if (rate >= failureRateThreshold) {
            transitionToOpenInternal();
        }
    }

    private void transitionToOpenInternal() {
        state = State.OPEN;
        openStateTimestampNano = System.nanoTime();
        halfOpenCallsAllowed = 0;
        halfOpenSuccessCount = 0;
    }

    private void transitionToClosedInternal() {
        state = State.CLOSED;
        openStateTimestampNano = 0L;
        halfOpenCallsAllowed = 0;
        halfOpenSuccessCount = 0;
        windowIndex = 0;
        windowCount = 0;
        Arrays.fill(window, (byte) -1);
    }

    private void transitionToHalfOpenInternal() {
        state = State.HALF_OPEN;
        halfOpenCallsAllowed = 0;
        halfOpenSuccessCount = 0;
    }

    public static final class Builder {
        private final String name;
        private double failureRateThreshold = 50.0;
        private int minimumCalls = 5;
        private Duration waitDurationInOpen = Duration.ofSeconds(5);
        private int permittedCallsInHalfOpen = 3;
        private int slidingWindowSize = 10;
        private Predicate<Throwable> recordException = t -> true;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
        }

        public Builder failureRateThreshold(double percentage) {
            if (percentage <= 0.0 || percentage > 100.0) {
                throw new IllegalArgumentException("failureRateThreshold must be between 0 and 100: " + percentage);
            }
            this.failureRateThreshold = percentage;
            return this;
        }

        public Builder minimumCalls(int minimumCalls) {
            if (minimumCalls < 1) {
                throw new IllegalArgumentException("minimumCalls must be at least 1: " + minimumCalls);
            }
            this.minimumCalls = minimumCalls;
            return this;
        }

        public Builder waitDurationInOpen(Duration duration) {
            Objects.requireNonNull(duration, "waitDurationInOpen cannot be null");
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("waitDurationInOpen must be positive: " + duration);
            }
            this.waitDurationInOpen = duration;
            return this;
        }

        public Builder permittedCallsInHalfOpen(int permittedCalls) {
            if (permittedCalls < 1) {
                throw new IllegalArgumentException("permittedCallsInHalfOpen must be at least 1: " + permittedCalls);
            }
            this.permittedCallsInHalfOpen = permittedCalls;
            return this;
        }

        public Builder slidingWindowSize(int size) {
            if (size < 1) {
                throw new IllegalArgumentException("slidingWindowSize must be at least 1: " + size);
            }
            this.slidingWindowSize = size;
            return this;
        }

        public Builder recordException(Predicate<Throwable> predicate) {
            this.recordException = Objects.requireNonNull(predicate, "predicate cannot be null");
            return this;
        }

        public Builder recordException(Class<? extends Throwable> exceptionClass) {
            Objects.requireNonNull(exceptionClass, "exceptionClass cannot be null");
            this.recordException = exceptionClass::isInstance;
            return this;
        }

        public CircuitBreaker build() {
            if (minimumCalls > slidingWindowSize) {
                this.minimumCalls = slidingWindowSize;
            }
            return new CircuitBreaker(this);
        }
    }
}
