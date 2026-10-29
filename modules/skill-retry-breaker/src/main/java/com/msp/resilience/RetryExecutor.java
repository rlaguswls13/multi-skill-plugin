package com.msp.resilience;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Executes operations with backoff retry policy.
 */
public final class RetryExecutor {

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final RetryPolicy policy;
    private final Sleeper sleeper;

    public RetryExecutor(RetryPolicy policy) {
        this(policy, duration -> {
            if (!duration.isZero() && !duration.isNegative()) {
                Thread.sleep(duration.toMillis());
            }
        });
    }

    public RetryExecutor(RetryPolicy policy, Sleeper sleeper) {
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper cannot be null");
    }

    public RetryPolicy policy() {
        return policy;
    }

    /**
     * Executes the supplier with retry logic. Returns value on success or throws RetryExhaustedException.
     */
    public <T> T execute(Supplier<T> action) {
        RetryResult<T> result = executeWithResult(action);
        if (result.isSuccess()) {
            return result.value();
        }
        Throwable cause = result.exception();
        throw new RetryExhaustedException(
                "Execution failed after " + result.attempts() + " attempts: " + (cause != null ? cause.getMessage() : "unknown"),
                cause,
                result.attempts(),
                result.totalDelay()
        );
    }

    /**
     * Runs the runnable with retry logic.
     */
    public void run(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Executes the supplier and returns detailed RetryResult containing attempt counts and total delay.
     */
    public <T> RetryResult<T> executeWithResult(Supplier<T> action) {
        Objects.requireNonNull(action, "action cannot be null");

        int attempt = 0;
        Duration totalDelay = Duration.ZERO;
        Throwable lastException = null;

        while (true) {
            attempt++;
            try {
                T value = action.get();
                return RetryResult.success(value, attempt, totalDelay);
            } catch (Throwable t) {
                lastException = t;

                if (!policy.canRetry(t, attempt)) {
                    return RetryResult.failure(lastException, attempt, totalDelay);
                }

                Duration delay = policy.computeDelay(attempt);
                totalDelay = totalDelay.plus(delay);

                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return RetryResult.failure(ie, attempt, totalDelay);
                }
            }
        }
    }
}
