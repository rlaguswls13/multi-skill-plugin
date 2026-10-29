package com.msp.singleflight;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Duplicate in-flight execution suppressor (Go-style SingleFlight Group).
 * <p>
 * When multiple threads invoke {@link #execute(Object, Callable)} concurrently for the same key,
 * only one thread invokes the underlying callable while all other concurrent callers await and
 * share the exact same result. This directly eliminates Cache Stampede (Thundering Herd) problems.
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class SingleFlight<K, V> {

    private final ConcurrentHashMap<K, Call<V>> calls = new ConcurrentHashMap<>();

    private static final class Call<V> {
        final CompletableFuture<V> future = new CompletableFuture<>();
        final AtomicInteger waiters = new AtomicInteger(0);
    }

    public static <K, V> SingleFlight<K, V> create() {
        return new SingleFlight<>();
    }

    /**
     * Executes the callable for the given key, suppressing duplicate concurrent executions.
     *
     * @param key      the cache/request key
     * @param callable the supplier to call if no execution is currently in flight
     * @return the result of the execution
     * @throws Exception if the underlying callable throws
     */
    public V execute(K key, Callable<V> callable) throws Exception {
        return executeWithResult(key, callable).value();
    }

    /**
     * Executes the callable for the given key and returns both the result and whether it was shared.
     *
     * @param key      the request key
     * @param callable the task to execute if not already in flight
     * @return {@link SingleFlightResult} indicating value and shared flag
     * @throws Exception if callable fails
     */
    public SingleFlightResult<V> executeWithResult(K key, Callable<V> callable) throws Exception {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(callable, "callable must not be null");

        Call<V> call = calls.computeIfAbsent(key, k -> new Call<>());
        int waiterCount = call.waiters.incrementAndGet();

        if (waiterCount == 1) {
            // Leader thread
            try {
                V result = callable.call();
                call.future.complete(result);
                return new SingleFlightResult<>(result, false);
            } catch (Throwable t) {
                call.future.completeExceptionally(t);
                if (t instanceof Exception e) {
                    throw e;
                }
                throw new RuntimeException(t);
            } finally {
                calls.remove(key, call);
            }
        } else {
            // Follower thread awaiting leader's result
            try {
                V result = call.future.get();
                return new SingleFlightResult<>(result, true);
            } catch (ExecutionException ee) {
                Throwable cause = ee.getCause();
                if (cause instanceof Exception e) {
                    throw e;
                }
                throw new RuntimeException(cause);
            }
        }
    }

    /**
     * Asynchronously executes a supplier, suppressing duplicate in-flight requests for the same key.
     *
     * @param key      the request key
     * @param supplier produces the CompletableFuture for the task
     * @return CompletableFuture completing with the task value
     */
    public CompletableFuture<V> executeAsync(K key, Supplier<CompletableFuture<V>> supplier) {
        return executeAsyncWithResult(key, supplier).thenApply(SingleFlightResult::value);
    }

    /**
     * Asynchronously executes a supplier, returning both the value and whether it was shared.
     */
    public CompletableFuture<SingleFlightResult<V>> executeAsyncWithResult(K key, Supplier<CompletableFuture<V>> supplier) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");

        Call<V> call = calls.computeIfAbsent(key, k -> new Call<>());
        int waiterCount = call.waiters.incrementAndGet();

        if (waiterCount == 1) {
            try {
                CompletableFuture<V> provided = supplier.get();
                if (provided == null) {
                    NullPointerException npe = new NullPointerException("supplier returned null CompletableFuture");
                    calls.remove(key, call);
                    call.future.completeExceptionally(npe);
                    return CompletableFuture.failedFuture(npe);
                }
                provided.whenComplete((val, ex) -> {
                    calls.remove(key, call);
                    if (ex != null) {
                        call.future.completeExceptionally(ex);
                    } else {
                        call.future.complete(val);
                    }
                });
                return provided.thenApply(val -> new SingleFlightResult<>(val, false));
            } catch (Throwable t) {
                calls.remove(key, call);
                call.future.completeExceptionally(t);
                return CompletableFuture.failedFuture(t);
            }
        } else {
            return call.future.thenApply(val -> new SingleFlightResult<>(val, true));
        }
    }

    /**
     * Evicts the in-flight registration for a key, allowing subsequent calls to launch a new execution.
     *
     * @param key the request key
     * @return true if an active in-flight call was present
     */
    public boolean forget(K key) {
        Objects.requireNonNull(key, "key must not be null");
        return calls.remove(key) != null;
    }

    /**
     * Returns true if there is an active in-flight execution for this key.
     */
    public boolean isInFlight(K key) {
        Objects.requireNonNull(key, "key must not be null");
        return calls.containsKey(key);
    }

    /**
     * Returns the number of currently active in-flight keys.
     */
    public int inFlightCount() {
        return calls.size();
    }

    /**
     * Clears all in-flight registrations.
     */
    public void reset() {
        calls.clear();
    }
}
