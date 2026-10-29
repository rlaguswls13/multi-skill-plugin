package com.msp.result;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 성공 값({@code T}) 또는 실패 원인({@code E})을 표현하는 함수형 불변 결과 인터페이스.
 * <p>
 * 예외를 강제로 던지지 않고도 메서드의 성공/실패 여부를 안전하고 명시적으로 반환·조합할 수 있습니다.
 *
 * @param <T> 성공 값의 타입
 * @param <E> 실패 원인의 타입
 */
public sealed interface Result<T, E> permits Result.Success, Result.Failure {

    static <T, E> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }

    boolean isSuccess();

    default boolean isFailure() {
        return !isSuccess();
    }

    T getOrNull();

    E getErrorOrNull();

    default T getOrElse(T fallback) {
        return isSuccess() ? getOrNull() : fallback;
    }

    default T getOrElseGet(Function<? super E, ? extends T> fallbackSupplier) {
        Objects.requireNonNull(fallbackSupplier);
        return isSuccess() ? getOrNull() : fallbackSupplier.apply(getErrorOrNull());
    }

    default T getOrThrow() {
        if (isSuccess()) {
            return getOrNull();
        }
        E error = getErrorOrNull();
        if (error instanceof RuntimeException re) {
            throw re;
        } else if (error instanceof Throwable t) {
            throw new RuntimeException("Result failure: " + t.getMessage(), t);
        }
        throw new NoSuchElementException("Result failure: " + error);
    }

    default <X extends Throwable> T getOrThrow(Function<? super E, ? extends X> exceptionFunction) throws X {
        Objects.requireNonNull(exceptionFunction);
        if (isSuccess()) {
            return getOrNull();
        }
        throw exceptionFunction.apply(getErrorOrNull());
    }

    default Optional<T> toOptional() {
        return isSuccess() ? Optional.ofNullable(getOrNull()) : Optional.empty();
    }

    default <R> Result<R, E> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        if (isSuccess()) {
            return success(mapper.apply(getOrNull()));
        }
        return failure(getErrorOrNull());
    }

    default <F> Result<T, F> mapError(Function<? super E, ? extends F> errorMapper) {
        Objects.requireNonNull(errorMapper);
        if (isFailure()) {
            return failure(errorMapper.apply(getErrorOrNull()));
        }
        return success(getOrNull());
    }

    @SuppressWarnings("unchecked")
    default <R> Result<R, E> flatMap(Function<? super T, ? extends Result<? extends R, ? extends E>> mapper) {
        Objects.requireNonNull(mapper);
        if (isSuccess()) {
            return (Result<R, E>) mapper.apply(getOrNull());
        }
        return failure(getErrorOrNull());
    }

    default Result<T, E> filter(Predicate<? super T> predicate, Function<? super T, ? extends E> errorOnFalse) {
        Objects.requireNonNull(predicate);
        Objects.requireNonNull(errorOnFalse);
        if (isSuccess()) {
            T value = getOrNull();
            if (!predicate.test(value)) {
                return failure(errorOnFalse.apply(value));
            }
        }
        return this;
    }

    default Result<T, E> recover(Function<? super E, ? extends T> recovery) {
        Objects.requireNonNull(recovery);
        if (isFailure()) {
            return success(recovery.apply(getErrorOrNull()));
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    default Result<T, E> recoverWith(Function<? super E, ? extends Result<? extends T, ? extends E>> recovery) {
        Objects.requireNonNull(recovery);
        if (isFailure()) {
            return (Result<T, E>) recovery.apply(getErrorOrNull());
        }
        return this;
    }

    default Result<T, E> onSuccess(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        if (isSuccess()) {
            action.accept(getOrNull());
        }
        return this;
    }

    default Result<T, E> onFailure(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        if (isFailure()) {
            action.accept(getErrorOrNull());
        }
        return this;
    }

    default <R> R fold(Function<? super T, ? extends R> successMapper, Function<? super E, ? extends R> failureMapper) {
        Objects.requireNonNull(successMapper);
        Objects.requireNonNull(failureMapper);
        return isSuccess() ? successMapper.apply(getOrNull()) : failureMapper.apply(getErrorOrNull());
    }

    record Success<T, E>(T value) implements Result<T, E> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public T getOrNull() {
            return value;
        }

        @Override
        public E getErrorOrNull() {
            return null;
        }
    }

    record Failure<T, E>(E error) implements Result<T, E> {
        public Failure {
            Objects.requireNonNull(error, "Failure의 error는 null일 수 없습니다.");
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
        public E getErrorOrNull() {
            return error;
        }
    }
}
