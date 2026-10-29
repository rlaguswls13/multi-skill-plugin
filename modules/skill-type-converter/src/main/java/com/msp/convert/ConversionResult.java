package com.msp.convert;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Encapsulates the result of a conversion operation, holding either a successfully converted value
 * or failure details.
 *
 * @param <T> converted value type
 */
public record ConversionResult<T>(boolean isSuccess, T value, Throwable cause) {

    public static <T> ConversionResult<T> success(T value) {
        return new ConversionResult<>(true, value, null);
    }

    public static <T> ConversionResult<T> failure(Throwable cause) {
        return new ConversionResult<>(false, null, cause);
    }

    public static <T> ConversionResult<T> failure(String rawValue, Class<T> targetType) {
        return new ConversionResult<>(false, null, new TypeCastException(rawValue, targetType));
    }

    public boolean isFailure() {
        return !isSuccess;
    }

    public T orElse(T fallback) {
        return isSuccess ? value : fallback;
    }

    public T orElseGet(Supplier<? extends T> fallbackSupplier) {
        Objects.requireNonNull(fallbackSupplier, "fallbackSupplier must not be null");
        return isSuccess ? value : fallbackSupplier.get();
    }

    public T orElseThrow() {
        if (isSuccess) {
            return value;
        }
        if (cause instanceof RuntimeException re) {
            throw re;
        }
        throw new IllegalStateException("Conversion failed", cause);
    }

    public <X extends Throwable> T orElseThrow(Function<Throwable, ? extends X> exceptionSupplier) throws X {
        if (isSuccess) {
            return value;
        }
        throw exceptionSupplier.apply(cause);
    }

    public Optional<T> toOptional() {
        return isSuccess ? Optional.ofNullable(value) : Optional.empty();
    }
}
