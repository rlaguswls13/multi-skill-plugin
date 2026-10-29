package com.msp.trace;

import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Thread-safe distributed trace context propagator with ThreadLocal storage,
 * AutoCloseable scope management, and asynchronous context propagation wrappers.
 */
public final class TraceIdPropagator {

    private static final ThreadLocal<TraceContext> CURRENT_CONTEXT = new ThreadLocal<>();
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private TraceIdPropagator() {
    }

    /**
     * Scope interface for managing TraceContext lifecycle with try-with-resources.
     */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Generates a 16-byte (32 lowercase hex characters) random TraceId.
     * Guarantees that the ID is non-zero.
     */
    public static String randomTraceId() {
        byte[] bytes = new byte[16];
        do {
            ThreadLocalRandom.current().nextBytes(bytes);
        } while (isAllZeroBytes(bytes));
        return HEX_FORMAT.formatHex(bytes);
    }

    /**
     * Generates an 8-byte (16 lowercase hex characters) random SpanId.
     * Guarantees that the ID is non-zero.
     */
    public static String randomSpanId() {
        byte[] bytes = new byte[8];
        do {
            ThreadLocalRandom.current().nextBytes(bytes);
        } while (isAllZeroBytes(bytes));
        return HEX_FORMAT.formatHex(bytes);
    }

    /**
     * Opens a new scope with the given trace context.
     * When the scope is closed, the previous context is restored.
     *
     * @param context the context to attach to the current thread (nullable to detach)
     * @return an AutoCloseable Scope
     */
    public static Scope open(TraceContext context) {
        TraceContext previous = CURRENT_CONTEXT.get();
        if (context != null) {
            CURRENT_CONTEXT.set(context);
        } else {
            CURRENT_CONTEXT.remove();
        }

        return () -> {
            if (previous != null) {
                CURRENT_CONTEXT.set(previous);
            } else {
                CURRENT_CONTEXT.remove();
            }
        };
    }

    /**
     * Retrieves the current TraceContext associated with this thread, if present.
     */
    public static Optional<TraceContext> current() {
        return Optional.ofNullable(CURRENT_CONTEXT.get());
    }

    /**
     * Retrieves the current TraceContext, or creates and attaches a new one if absent.
     */
    public static TraceContext currentOrNew() {
        TraceContext ctx = CURRENT_CONTEXT.get();
        if (ctx == null) {
            ctx = TraceContext.create();
            CURRENT_CONTEXT.set(ctx);
        }
        return ctx;
    }

    /**
     * Clears the current ThreadLocal context.
     */
    public static void clear() {
        CURRENT_CONTEXT.remove();
    }

    /**
     * Wraps a Runnable to propagate the current thread's TraceContext to the execution thread.
     */
    public static Runnable wrap(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        TraceContext captured = CURRENT_CONTEXT.get();
        return () -> {
            try (Scope ignored = open(captured)) {
                runnable.run();
            }
        };
    }

    /**
     * Wraps a Callable to propagate the current thread's TraceContext to the execution thread.
     */
    public static <T> Callable<T> wrap(Callable<T> callable) {
        Objects.requireNonNull(callable, "callable must not be null");
        TraceContext captured = CURRENT_CONTEXT.get();
        return () -> {
            try (Scope ignored = open(captured)) {
                return callable.call();
            }
        };
    }

    /**
     * Wraps a Supplier to propagate the current thread's TraceContext to the execution thread.
     */
    public static <T> Supplier<T> wrapSupplier(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        TraceContext captured = CURRENT_CONTEXT.get();
        return () -> {
            try (Scope ignored = open(captured)) {
                return supplier.get();
            }
        };
    }

    /**
     * Wraps a Consumer to propagate the current thread's TraceContext to the execution thread.
     */
    public static <T> Consumer<T> wrapConsumer(Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        TraceContext captured = CURRENT_CONTEXT.get();
        return item -> {
            try (Scope ignored = open(captured)) {
                consumer.accept(item);
            }
        };
    }

    /**
     * Wraps a Function to propagate the current thread's TraceContext to the execution thread.
     */
    public static <T, R> Function<T, R> wrapFunction(Function<T, R> function) {
        Objects.requireNonNull(function, "function must not be null");
        TraceContext captured = CURRENT_CONTEXT.get();
        return input -> {
            try (Scope ignored = open(captured)) {
                return function.apply(input);
            }
        };
    }

    private static boolean isAllZeroBytes(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) return false;
        }
        return true;
    }
}
