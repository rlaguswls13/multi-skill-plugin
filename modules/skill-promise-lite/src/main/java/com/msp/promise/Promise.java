package com.msp.promise;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fluent Promise abstraction wrapping Java 21's {@link CompletableFuture}.
 * Provides JavaScript-like chaining, error handling, timeout fallbacks,
 * and standard combinators ({@code allOf}, {@code anyOf}, {@code race}).
 *
 * @param <T> value type
 */
public final class Promise<T> {

    private final CompletableFuture<T> future;

    private Promise(CompletableFuture<T> future) {
        this.future = Objects.requireNonNull(future, "future must not be null");
    }

    // ==========================================
    // Static Factories
    // ==========================================

    public static <T> Promise<T> resolve(T value) {
        return new Promise<>(CompletableFuture.completedFuture(value));
    }

    public static <T> Promise<T> reject(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable must not be null");
        return new Promise<>(CompletableFuture.failedFuture(throwable));
    }

    public static <T> Promise<T> supplyAsync(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return new Promise<>(CompletableFuture.supplyAsync(supplier));
    }

    public static <T> Promise<T> supplyAsync(Supplier<T> supplier, Executor executor) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        return new Promise<>(CompletableFuture.supplyAsync(supplier, executor));
    }

    public static Promise<Void> runAsync(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        return new Promise<>(CompletableFuture.runAsync(runnable));
    }

    public static Promise<Void> runAsync(Runnable runnable, Executor executor) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        return new Promise<>(CompletableFuture.runAsync(runnable, executor));
    }

    public static <T> Promise<T> from(CompletableFuture<T> future) {
        return new Promise<>(future);
    }

    public static <T> Promise<T> from(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage must not be null");
        return new Promise<>(stage.toCompletableFuture());
    }

    public static <T> Promise<T> create(PromiseExecutor<T> executor) {
        Objects.requireNonNull(executor, "executor must not be null");
        CompletableFuture<T> cf = new CompletableFuture<>();
        try {
            executor.execute(cf::complete, cf::completeExceptionally);
        } catch (Throwable t) {
            cf.completeExceptionally(t);
        }
        return new Promise<>(cf);
    }

    // ==========================================
    // Fluent Chaining
    // ==========================================

    public <R> Promise<R> thenApply(Function<? super T, ? extends R> fn) {
        Objects.requireNonNull(fn, "fn must not be null");
        return new Promise<>(future.thenApply(fn));
    }

    public <R> Promise<R> thenCompose(Function<? super T, Promise<R>> fn) {
        Objects.requireNonNull(fn, "fn must not be null");
        return new Promise<>(future.thenCompose(val -> fn.apply(val).toCompletableFuture()));
    }

    public Promise<Void> thenAccept(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action must not be null");
        return new Promise<>(future.thenAccept(action));
    }

    public Promise<Void> thenRun(Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        return new Promise<>(future.thenRun(action));
    }

    public Promise<T> catchError(Function<Throwable, ? extends T> fallback) {
        Objects.requireNonNull(fallback, "fallback must not be null");
        CompletableFuture<T> handled = future.handle((value, ex) -> {
            if (ex == null) {
                return CompletableFuture.completedFuture(value);
            }
            Throwable cause = unwrap(ex);
            try {
                return CompletableFuture.completedFuture(fallback.apply(cause));
            } catch (Throwable t) {
                return CompletableFuture.<T>failedFuture(t);
            }
        }).thenCompose(Function.identity());
        return new Promise<>(handled);
    }

    public <E extends Throwable> Promise<T> catchError(Class<E> errorType, Function<E, ? extends T> fallback) {
        Objects.requireNonNull(errorType, "errorType must not be null");
        Objects.requireNonNull(fallback, "fallback must not be null");
        CompletableFuture<T> handled = future.handle((value, ex) -> {
            if (ex == null) {
                return CompletableFuture.completedFuture(value);
            }
            Throwable cause = unwrap(ex);
            if (errorType.isInstance(cause)) {
                try {
                    return CompletableFuture.completedFuture(fallback.apply(errorType.cast(cause)));
                } catch (Throwable t) {
                    return CompletableFuture.<T>failedFuture(t);
                }
            }
            return CompletableFuture.<T>failedFuture(cause);
        }).thenCompose(Function.identity());
        return new Promise<>(handled);
    }

    public Promise<T> finallyDo(Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        return new Promise<>(future.whenComplete((v, ex) -> action.run()));
    }

    // ==========================================
    // Timeout
    // ==========================================

    public Promise<T> timeout(Duration duration) {
        Objects.requireNonNull(duration, "duration must not be null");
        return new Promise<>(future.copy().orTimeout(duration.toNanos(), TimeUnit.NANOSECONDS));
    }

    public Promise<T> timeout(Duration duration, T fallbackValue) {
        Objects.requireNonNull(duration, "duration must not be null");
        return new Promise<>(future.copy().completeOnTimeout(fallbackValue, duration.toNanos(), TimeUnit.NANOSECONDS));
    }

    public Promise<T> timeout(Duration duration, Supplier<T> fallbackSupplier) {
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(fallbackSupplier, "fallbackSupplier must not be null");
        CompletableFuture<T> timed = future.copy().orTimeout(duration.toNanos(), TimeUnit.NANOSECONDS)
                .handle((val, ex) -> {
                    if (ex == null) {
                        return val;
                    }
                    Throwable cause = unwrap(ex);
                    if (cause instanceof TimeoutException) {
                        return fallbackSupplier.get();
                    }
                    if (ex instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new CompletionException(ex);
                });
        return new Promise<>(timed);
    }

    // ==========================================
    // Combinators
    // ==========================================

    @SafeVarargs
    public static Promise<List<Object>> allOf(Promise<?>... promises) {
        return allOf(Arrays.asList(promises));
    }

    public static Promise<List<Object>> allOf(List<Promise<?>> promises) {
        Objects.requireNonNull(promises, "promises must not be null");
        if (promises.isEmpty()) {
            return Promise.resolve(Collections.emptyList());
        }

        CompletableFuture<?>[] cfs = promises.stream()
                .map(Promise::toCompletableFuture)
                .toArray(CompletableFuture[]::new);

        CompletableFuture<List<Object>> allFuture = CompletableFuture.allOf(cfs).thenApply(v -> {
            List<Object> list = new ArrayList<>(cfs.length);
            for (CompletableFuture<?> cf : cfs) {
                list.add(cf.join());
            }
            return Collections.unmodifiableList(list);
        });

        return new Promise<>(allFuture);
    }

    public static <T> Promise<List<T>> allOfTyped(List<Promise<T>> promises) {
        Objects.requireNonNull(promises, "promises must not be null");
        if (promises.isEmpty()) {
            return Promise.resolve(Collections.emptyList());
        }

        CompletableFuture<?>[] cfs = promises.stream()
                .map(Promise::toCompletableFuture)
                .toArray(CompletableFuture[]::new);

        CompletableFuture<List<T>> allFuture = CompletableFuture.allOf(cfs).thenApply(v -> {
            List<T> list = new ArrayList<>(promises.size());
            for (Promise<T> p : promises) {
                list.add(p.join());
            }
            return Collections.unmodifiableList(list);
        });

        return new Promise<>(allFuture);
    }

    @SafeVarargs
    public static Promise<Object> anyOf(Promise<?>... promises) {
        return anyOf(Arrays.asList(promises));
    }

    /**
     * Resolves with the first promise that fulfills successfully.
     * If all promises reject, rejects with {@link AggregateException}.
     */
    public static Promise<Object> anyOf(List<Promise<?>> promises) {
        Objects.requireNonNull(promises, "promises must not be null");
        if (promises.isEmpty()) {
            return Promise.reject(new NoSuchElementException("No promises provided to anyOf"));
        }

        CompletableFuture<Object> anyFuture = new CompletableFuture<>();
        AtomicInteger remaining = new AtomicInteger(promises.size());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (Promise<?> p : promises) {
            p.toCompletableFuture().whenComplete((val, ex) -> {
                if (ex == null) {
                    anyFuture.complete(val);
                } else {
                    errors.add(unwrap(ex));
                    if (remaining.decrementAndGet() == 0) {
                        anyFuture.completeExceptionally(new AggregateException("All promises were rejected", errors));
                    }
                }
            });
        }

        return new Promise<>(anyFuture);
    }

    @SafeVarargs
    public static Promise<Object> race(Promise<?>... promises) {
        return race(Arrays.asList(promises));
    }

    /**
     * Settles (resolves or rejects) as soon as the first promise settles.
     */
    public static Promise<Object> race(List<Promise<?>> promises) {
        Objects.requireNonNull(promises, "promises must not be null");
        if (promises.isEmpty()) {
            return Promise.reject(new NoSuchElementException("No promises provided to race"));
        }

        CompletableFuture<Object> raceFuture = new CompletableFuture<>();
        for (Promise<?> p : promises) {
            p.toCompletableFuture().whenComplete((val, ex) -> {
                if (ex != null) {
                    raceFuture.completeExceptionally(unwrap(ex));
                } else {
                    raceFuture.complete(val);
                }
            });
        }

        return new Promise<>(raceFuture);
    }

    // ==========================================
    // Interop & Terminal Inspection
    // ==========================================

    public T join() {
        return future.join();
    }

    public T get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    public T get(Duration timeout) throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(timeout, "timeout must not be null");
        return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    public T orElse(T other) {
        try {
            return future.join();
        } catch (Throwable t) {
            return other;
        }
    }

    public T orElseGet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        try {
            return future.join();
        } catch (Throwable t) {
            return supplier.get();
        }
    }

    public boolean isDone() {
        return future.isDone();
    }

    public boolean isCompletedExceptionally() {
        return future.isCompletedExceptionally();
    }

    public boolean isCancelled() {
        return future.isCancelled();
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    public CompletableFuture<T> toCompletableFuture() {
        return future;
    }

    private static Throwable unwrap(Throwable ex) {
        if (ex instanceof CompletionException && ex.getCause() != null) {
            return unwrap(ex.getCause());
        }
        if (ex instanceof ExecutionException && ex.getCause() != null) {
            return unwrap(ex.getCause());
        }
        return ex;
    }
}
