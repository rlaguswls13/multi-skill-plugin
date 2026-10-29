package com.msp.result.function;

@FunctionalInterface
public interface ThrowingSupplier<T> {
    T get() throws Throwable;
}
