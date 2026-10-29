package com.msp.result.function;

@FunctionalInterface
public interface ThrowingFunction<T, R> {
    R apply(T t) throws Throwable;
}
