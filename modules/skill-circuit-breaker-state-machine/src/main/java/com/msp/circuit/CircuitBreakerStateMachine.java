package com.msp.circuit;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * High-performance, precision thread-safe state machine for the Circuit Breaker pattern.
 * <p>
 * Supports CLOSED, OPEN, and HALF_OPEN states with sliding window metrics,
 * configurable failure thresholds, and automatic probe recovery.
 */
public final class CircuitBreakerStateMachine {

    private final CircuitBreakerConfig config;
    private final LongSupplier timeProvider;
    private final ReentrantLock lock = new ReentrantLock();

    private CircuitState state = CircuitState.CLOSED;
    private long lastStateTransitionTime;

    // Sliding window for CLOSED state
    private final boolean[] slidingWindow; // true = failure, false = success
    private int windowHead = 0;
    private int windowSize = 0;
    private int failureCount = 0;

    // Counters for HALF_OPEN state
    private int halfOpenPermitsAcquired = 0;
    private int halfOpenSuccesses = 0;
    private int halfOpenFailures = 0;

    public CircuitBreakerStateMachine(CircuitBreakerConfig config) {
        this(config, System::currentTimeMillis);
    }

    public CircuitBreakerStateMachine(CircuitBreakerConfig config, LongSupplier timeProvider) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider must not be null");
        this.slidingWindow = new boolean[config.slidingWindowSize()];
        this.lastStateTransitionTime = timeProvider.getAsLong();
    }

    /**
     * Checks if a request is permitted to proceed.
     * <p>
     * - In CLOSED state: always returns true.
     * - In OPEN state: returns true and transitions to HALF_OPEN if the wait duration has elapsed.
     * - In HALF_OPEN state: returns true if permitted probe call quota has not been exhausted.
     *
     * @return true if call is permitted, false otherwise
     */
    public boolean tryAcquirePermission() {
        lock.lock();
        try {
            long now = timeProvider.getAsLong();
            switch (state) {
                case CLOSED:
                    return true;

                case OPEN:
                    long elapsed = now - lastStateTransitionTime;
                    if (elapsed >= config.waitDurationInOpen().toMillis()) {
                        transitionToHalfOpenInternal(now);
                        halfOpenPermitsAcquired = 1;
                        return true;
                    }
                    return false;

                case HALF_OPEN:
                    if (halfOpenPermitsAcquired < config.permittedNumberOfCallsInHalfOpenState()) {
                        halfOpenPermitsAcquired++;
                        return true;
                    }
                    return false;

                default:
                    return false;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Records a successful execution.
     */
    public void recordSuccess() {
        lock.lock();
        try {
            long now = timeProvider.getAsLong();
            if (state == CircuitState.CLOSED) {
                recordResultInWindow(false);
            } else if (state == CircuitState.HALF_OPEN) {
                halfOpenSuccesses++;
                checkHalfOpenCompletion(now);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Records a failed execution.
     */
    public void recordFailure() {
        lock.lock();
        try {
            long now = timeProvider.getAsLong();
            if (state == CircuitState.CLOSED) {
                recordResultInWindow(true);
                evaluateClosedStateTrip(now);
            } else if (state == CircuitState.HALF_OPEN) {
                halfOpenFailures++;
                checkHalfOpenCompletion(now);
            }
        } finally {
            lock.unlock();
        }
    }

    private void recordResultInWindow(boolean isFailure) {
        if (windowSize == config.slidingWindowSize()) {
            boolean evicted = slidingWindow[windowHead];
            if (evicted) {
                failureCount--;
            }
        } else {
            windowSize++;
        }

        slidingWindow[windowHead] = isFailure;
        if (isFailure) {
            failureCount++;
        }

        windowHead = (windowHead + 1) % config.slidingWindowSize();
    }

    private void evaluateClosedStateTrip(long now) {
        if (windowSize >= config.minimumNumberOfCalls()) {
            float currentFailureRate = ((float) failureCount / windowSize) * 100.0f;
            if (currentFailureRate >= config.failureRateThreshold()) {
                transitionToOpenInternal(now);
            }
        }
    }

    private void checkHalfOpenCompletion(long now) {
        int completed = halfOpenSuccesses + halfOpenFailures;
        float currentFailureRate = ((float) halfOpenFailures / completed) * 100.0f;

        // If failure threshold reached or all trial calls completed
        if (currentFailureRate >= config.failureRateThreshold()) {
            transitionToOpenInternal(now);
        } else if (completed >= config.permittedNumberOfCallsInHalfOpenState()) {
            transitionToClosedInternal(now);
        }
    }

    private void transitionToOpenInternal(long now) {
        this.state = CircuitState.OPEN;
        this.lastStateTransitionTime = now;
        resetWindow();
    }

    private void transitionToHalfOpenInternal(long now) {
        this.state = CircuitState.HALF_OPEN;
        this.lastStateTransitionTime = now;
        this.halfOpenPermitsAcquired = 0;
        this.halfOpenSuccesses = 0;
        this.halfOpenFailures = 0;
    }

    private void transitionToClosedInternal(long now) {
        this.state = CircuitState.CLOSED;
        this.lastStateTransitionTime = now;
        resetWindow();
    }

    private void resetWindow() {
        this.windowHead = 0;
        this.windowSize = 0;
        this.failureCount = 0;
    }

    /**
     * Resets the circuit breaker to CLOSED state with cleared metrics.
     */
    public void reset() {
        lock.lock();
        try {
            transitionToClosedInternal(timeProvider.getAsLong());
        } finally {
            lock.unlock();
        }
    }

    public void transitionToOpen() {
        lock.lock();
        try {
            transitionToOpenInternal(timeProvider.getAsLong());
        } finally {
            lock.unlock();
        }
    }

    public void transitionToClosed() {
        lock.lock();
        try {
            transitionToClosedInternal(timeProvider.getAsLong());
        } finally {
            lock.unlock();
        }
    }

    public void transitionToHalfOpen() {
        lock.lock();
        try {
            transitionToHalfOpenInternal(timeProvider.getAsLong());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes the supplier protected by the circuit breaker.
     *
     * @param supplier computation to execute
     * @param <T>      return type
     * @return result of computation
     * @throws CircuitBreakerOpenException if call is blocked
     */
    public <T> T executeSupplier(Supplier<T> supplier) {
        if (!tryAcquirePermission()) {
            throw new CircuitBreakerOpenException("CircuitBreaker is in state " + state + " and rejected call");
        }
        try {
            T result = supplier.get();
            recordSuccess();
            return result;
        } catch (RuntimeException | Error e) {
            recordFailure();
            throw e;
        }
    }

    /**
     * Executes the runnable protected by the circuit breaker.
     */
    public void executeRunnable(Runnable runnable) {
        executeSupplier(() -> {
            runnable.run();
            return null;
        });
    }

    public CircuitState getState() {
        lock.lock();
        try {
            // Check if open duration has passed to reflect HALF_OPEN
            if (state == CircuitState.OPEN) {
                long now = timeProvider.getAsLong();
                if (now - lastStateTransitionTime >= config.waitDurationInOpen().toMillis()) {
                    transitionToHalfOpenInternal(now);
                }
            }
            return state;
        } finally {
            lock.unlock();
        }
    }

    public CircuitBreakerConfig getConfig() {
        return config;
    }

    public float getFailureRate() {
        lock.lock();
        try {
            if (state == CircuitState.CLOSED) {
                return (windowSize == 0) ? 0.0f : ((float) failureCount / windowSize) * 100.0f;
            } else if (state == CircuitState.HALF_OPEN) {
                int total = halfOpenSuccesses + halfOpenFailures;
                return (total == 0) ? 0.0f : ((float) halfOpenFailures / total) * 100.0f;
            }
            return 100.0f;
        } finally {
            lock.unlock();
        }
    }
}
