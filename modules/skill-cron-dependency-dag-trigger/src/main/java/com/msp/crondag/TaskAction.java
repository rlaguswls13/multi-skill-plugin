package com.msp.crondag;

/**
 * Functional interface for task execution logic receiving execution context.
 *
 * @param <T> Return type
 */
@FunctionalInterface
public interface TaskAction<T> {
    T execute(TaskExecutionContext context) throws Exception;
}
