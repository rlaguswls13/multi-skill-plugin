package com.msp.shutdown;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of shutting down a single registered component.
 *
 * @param name      component name
 * @param order     execution phase / order priority
 * @param duration  time taken to close this component
 * @param isSuccess true if closed without exception or timeout
 * @param error     thrown exception or timeout reason, or null if successful
 */
public record ComponentShutdownResult(
        String name,
        int order,
        Duration duration,
        boolean isSuccess,
        Throwable error
) {
    public ComponentShutdownResult {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
    }

    public static ComponentShutdownResult success(String name, int order, Duration duration) {
        return new ComponentShutdownResult(name, order, duration, true, null);
    }

    public static ComponentShutdownResult failure(String name, int order, Duration duration, Throwable error) {
        return new ComponentShutdownResult(name, order, duration, false, error);
    }

    public Optional<Throwable> errorOptional() {
        return Optional.ofNullable(error);
    }
}
