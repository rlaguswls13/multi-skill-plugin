package com.msp.fanout;

import java.util.Optional;

/**
 * Encapsulates the execution outcome of an individual task in a fan-out operation.
 *
 * @param <T>   result value type
 * @param index zero-based index of the task in the submitted order
 * @param value value produced by task if successful, or null
 * @param error exception thrown by task if failed, or null
 * @param isSuccess true if succeeded, false if failed or timed out
 */
public record TaskResult<T>(
        int index,
        T value,
        Throwable error,
        boolean isSuccess
) {

    public static <T> TaskResult<T> success(int index, T value) {
        return new TaskResult<>(index, value, null, true);
    }

    public static <T> TaskResult<T> failure(int index, Throwable error) {
        return new TaskResult<>(index, null, error, false);
    }

    public Optional<T> valueOptional() {
        return Optional.ofNullable(value);
    }

    public Optional<Throwable> errorOptional() {
        return Optional.ofNullable(error);
    }
}
