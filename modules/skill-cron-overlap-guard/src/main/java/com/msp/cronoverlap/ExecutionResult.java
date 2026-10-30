package com.msp.cronoverlap;

import java.time.Duration;
import java.util.Optional;

public record ExecutionResult<T>(
        ExecutionStatus status,
        T value,
        Duration duration,
        Throwable error
) {
    public boolean wasExecuted() {
        return status == ExecutionStatus.EXECUTED;
    }

    public boolean wasSkipped() {
        return status == ExecutionStatus.SKIPPED_ALREADY_RUNNING;
    }

    public Optional<T> optionalValue() {
        return Optional.ofNullable(value);
    }
}
