package com.msp.result.function;

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Throwable;
}
