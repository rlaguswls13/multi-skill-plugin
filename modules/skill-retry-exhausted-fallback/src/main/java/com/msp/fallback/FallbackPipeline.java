package com.msp.fallback;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * 1차 호출 실패 시 대체값 주입, 폴백 함수 실행, Dead-Letter Queue 격리를 순차 처리하는 안전 복구 파이프라인.
 */
public final class FallbackPipeline<T> {

    private final Callable<T> primaryAction;
    private final Function<Throwable, T> fallbackFunction;
    private final DeadLetterQueue<T> deadLetterQueue;
    private final String taskIdentifier;

    private FallbackPipeline(Builder<T> builder) {
        this.primaryAction = Objects.requireNonNull(builder.primaryAction, "primaryAction must not be null");
        this.fallbackFunction = builder.fallbackFunction;
        this.deadLetterQueue = builder.deadLetterQueue;
        this.taskIdentifier = builder.taskIdentifier;
    }

    public static <T> Builder<T> of(Callable<T> primaryAction) {
        return new Builder<>(primaryAction);
    }

    public FallbackResult<T> execute() {
        try {
            T value = primaryAction.call();
            return FallbackResult.success(value);
        } catch (Throwable cause) {
            T fallbackVal = null;
            if (fallbackFunction != null) {
                try {
                    fallbackVal = fallbackFunction.apply(cause);
                } catch (Throwable fallbackErr) {
                    cause.addSuppressed(fallbackErr);
                }
            }

            if (deadLetterQueue != null) {
                deadLetterQueue.record(taskIdentifier != null ? taskIdentifier : "unnamed-task", fallbackVal, cause);
            }

            return FallbackResult.fallback(fallbackVal, cause, "FALLBACK_HANDLER");
        }
    }

    public static final class Builder<T> {
        private final Callable<T> primaryAction;
        private Function<Throwable, T> fallbackFunction = null;
        private DeadLetterQueue<T> deadLetterQueue = null;
        private String taskIdentifier = null;

        private Builder(Callable<T> primaryAction) {
            this.primaryAction = primaryAction;
        }

        public Builder<T> fallbackValue(T value) {
            this.fallbackFunction = ex -> value;
            return this;
        }

        public Builder<T> fallbackFunction(Function<Throwable, T> fallbackFn) {
            this.fallbackFunction = fallbackFn;
            return this;
        }

        public Builder<T> deadLetterQueue(DeadLetterQueue<T> dlq, String taskIdentifier) {
            this.deadLetterQueue = dlq;
            this.taskIdentifier = taskIdentifier;
            return this;
        }

        public FallbackPipeline<T> build() {
            return new FallbackPipeline<>(this);
        }

        public FallbackResult<T> execute() {
            return build().execute();
        }
    }
}
