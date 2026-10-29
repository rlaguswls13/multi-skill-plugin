package com.msp.promise;

import java.util.function.Consumer;

/**
 * Functional callback for creating promises in JavaScript-style {@code new Promise((resolve, reject) -> ...)}.
 *
 * @param <T> result type
 */
@FunctionalInterface
public interface PromiseExecutor<T> {
    void execute(Consumer<T> resolve, Consumer<Throwable> reject) throws Exception;
}
