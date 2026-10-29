package com.msp.result;

import com.msp.result.function.ThrowingFunction;
import com.msp.result.function.ThrowingRunnable;
import com.msp.result.function.ThrowingSupplier;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 예외가 발생할 수 있는 코드 블록을 감싸 안전하게 처리하는 예외 캡슐화 객체.
 *
 * @param <T> 연산 성공 시의 반환 타입
 */
public sealed interface Try<T> permits Try.Success, Try.Failure {

    static <T> Try<T> of(ThrowingSupplier<T> supplier) {
        Objects.requireNonNull(supplier);
        try {
            return new Success<>(supplier.get());
        } catch (Throwable t) {
            return new Failure<>(t);
        }
    }

    static Try<Void> run(ThrowingRunnable runnable) {
        Objects.requireNonNull(runnable);
        try {
            runnable.run();
            return new Success<>(null);
        } catch (Throwable t) {
            return new Failure<>(t);
        }
    }

    static <T> Try<T> success(T value) {
        return new Success<>(value);
    }

    static <T> Try<T> failure(Throwable cause) {
        return new Failure<>(cause);
    }

    boolean isSuccess();

    default boolean isFailure() {
        return !isSuccess();
    }

    T getOrNull();

    Throwable getCause();

    default T getOrElse(T fallback) {
        return isSuccess() ? getOrNull() : fallback;
    }

    default T getOrThrow() {
        if (isSuccess()) {
            return getOrNull();
        }
        Throwable cause = getCause();
        if (cause instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException("Try execution failed: " + cause.getMessage(), cause);
    }

    default <R> Try<R> map(ThrowingFunction<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        if (isSuccess()) {
            try {
                return success(mapper.apply(getOrNull()));
            } catch (Throwable t) {
                return failure(t);
            }
        }
        return failure(getCause());
    }

    @SuppressWarnings("unchecked")
    default <R> Try<R> flatMap(ThrowingFunction<? super T, ? extends Try<? extends R>> mapper) {
        Objects.requireNonNull(mapper);
        if (isSuccess()) {
            try {
                return (Try<R>) mapper.apply(getOrNull());
            } catch (Throwable t) {
                return failure(t);
            }
        }
        return failure(getCause());
    }

    default Try<T> recover(Function<? super Throwable, ? extends T> recovery) {
        Objects.requireNonNull(recovery);
        if (isFailure()) {
            try {
                return success(recovery.apply(getCause()));
            } catch (Throwable t) {
                return failure(t);
            }
        }
        return this;
    }

    default Try<T> onSuccess(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        if (isSuccess()) {
            action.accept(getOrNull());
        }
        return this;
    }

    default Try<T> onFailure(Consumer<? super Throwable> action) {
        Objects.requireNonNull(action);
        if (isFailure()) {
            action.accept(getCause());
        }
        return this;
    }

    default Optional<T> toOptional() {
        return isSuccess() ? Optional.ofNullable(getOrNull()) : Optional.empty();
    }

    default Result<T, Throwable> toResult() {
        return isSuccess() ? Result.success(getOrNull()) : Result.failure(getCause());
    }

    default <E> Result<T, E> toResult(Function<? super Throwable, ? extends E> errorMapper) {
        Objects.requireNonNull(errorMapper);
        return isSuccess()
                ? Result.success(getOrNull())
                : Result.failure(errorMapper.apply(getCause()));
    }

    record Success<T>(T value) implements Try<T> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public T getOrNull() {
            return value;
        }

        @Override
        public Throwable getCause() {
            return null;
        }
    }

    record Failure<T>(Throwable cause) implements Try<T> {
        public Failure {
            Objects.requireNonNull(cause, "Try.Failure의 cause는 null일 수 없습니다.");
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public T getOrNull() {
            return null;
        }

        @Override
        public Throwable getCause() {
            return cause;
        }
    }
}
