package com.msp.hedged;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * Tail-Latency (P99) Reduction Hedged Request Executor in pure Java 21.
 * <p>
 * Architecture:
 * <ul>
 *     <li>Fires the primary request immediately (attempt 0).</li>
 *     <li>If the primary request does not complete within {@code hedgeDelay}, fires up to {@code maxHedgedAttempts}
 *         secondary speculative requests spaced by {@code hedgeDelay} (or immediately if a retriable failure occurs).</li>
 *     <li>Adopts the first successful result ({@code firstCompleted}) and automatically cancels all remaining
 *         in-flight redundant attempts and scheduled hedge timers.</li>
 *     <li>Configurable {@code hedgeOnExceptionPredicate}: if an exception matches the predicate, it is treated as
 *         retriable/hedgeable and immediately triggers the next available speculative hedge attempt rather than
 *         waiting for the timer; if it does not match (non-retriable fatal exception), the entire execution fails
 *         immediately and cancels any other in-flight attempts.</li>
 * </ul>
 */
public final class HedgedRequestExecutor implements AutoCloseable {

    private final Duration hedgeDelay;
    private final int maxHedgedAttempts;
    private final Predicate<Throwable> hedgeOnExceptionPredicate;
    private final ExecutorService workerExecutor;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsWorkerExecutor;
    private final boolean ownsScheduler;

    private final LongAdder primaryWins = new LongAdder();
    private final LongAdder hedgedWins = new LongAdder();
    private final LongAdder totalHedgesFired = new LongAdder();
    private final LongAdder cancelledRedundantCalls = new LongAdder();

    private HedgedRequestExecutor(Builder builder) {
        this.hedgeDelay = builder.hedgeDelay;
        this.maxHedgedAttempts = builder.maxHedgedAttempts;
        this.hedgeOnExceptionPredicate = builder.hedgeOnExceptionPredicate;
        if (builder.workerExecutor != null) {
            this.workerExecutor = builder.workerExecutor;
            this.ownsWorkerExecutor = false;
        } else {
            this.workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
            this.ownsWorkerExecutor = true;
        }
        if (builder.scheduler != null) {
            this.scheduler = builder.scheduler;
            this.ownsScheduler = false;
        } else {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = Thread.ofPlatform().daemon(true).name("hedged-scheduler").unstarted(r);
                return t;
            });
            this.ownsScheduler = true;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Executes a synchronous supplier using hedged request speculation and blocks until the first successful result
     * or fatal/exhausted failure.
     *
     * @param task Callable task to execute for primary and hedged attempts
     * @param <T>  Return type
     * @return The winning result value
     * @throws ExecutionException   if all attempts fail or a non-hedgeable exception occurs
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public <T> T execute(Callable<T> task) throws ExecutionException, InterruptedException {
        return executeWithMetadata(attemptIndex -> task.call()).value();
    }

    /**
     * Executes an attempt-aware task using hedged request speculation and returns rich {@link HedgedResult} metadata.
     *
     * @param attemptTask Function receiving attempt index (0 = primary, 1..N = hedged) and returning result
     * @param <T>         Return type
     * @return {@link HedgedResult} with value and winning attempt details
     * @throws ExecutionException   if all attempts fail or a non-hedgeable exception occurs
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public <T> HedgedResult<T> executeWithMetadata(AttemptCallable<T> attemptTask)
            throws ExecutionException, InterruptedException {
        Objects.requireNonNull(attemptTask, "attemptTask must not be null");
        return submitAsync(attemptIndex -> CompletableFuture.supplyAsync(() -> {
            try {
                return attemptTask.call(attemptIndex);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, workerExecutor)).get();
    }

    /**
     * Executes an asynchronous task supplier returning a {@link CompletableFuture} for each attempt.
     *
     * @param asyncTask Supplier of CompletableFuture for each attempt (attemptIndex: 0 = primary, 1..N = hedged)
     * @param <T>       Result type
     * @return CompletableFuture resolving to {@link HedgedResult}
     */
    public <T> CompletableFuture<HedgedResult<T>> submitAsync(IntFunction<CompletableFuture<T>> asyncTask) {
        Objects.requireNonNull(asyncTask, "asyncTask must not be null");
        long startNanos = System.nanoTime();
        int totalAllowedAttempts = 1 + maxHedgedAttempts;

        CompletableFuture<HedgedResult<T>> masterFuture = new CompletableFuture<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicInteger launchedAttempts = new AtomicInteger(0);
        AtomicInteger failedAttempts = new AtomicInteger(0);
        AtomicInteger hedgesFiredForThisCall = new AtomicInteger(0);

        List<Future<?>> inFlightFutures = new CopyOnWriteArrayList<>();
        List<ScheduledFuture<?>> scheduledTimers = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        Runnable launchNextAttempt = new Runnable() {
            @Override
            public void run() {
                if (completed.get() || masterFuture.isDone()) {
                    return;
                }
                int currentAttempt = launchedAttempts.getAndIncrement();
                if (currentAttempt >= totalAllowedAttempts) {
                    launchedAttempts.decrementAndGet();
                    return;
                }

                if (currentAttempt > 0) {
                    hedgesFiredForThisCall.incrementAndGet();
                    totalHedgesFired.increment();
                }

                // Submit task on workerExecutor so that Future.cancel(true) interrupts the running thread
                // AND we also cancel the CompletableFuture returned by asyncTask.
                CompletableFuture<T> rawAttemptCf;
                try {
                    rawAttemptCf = asyncTask.apply(currentAttempt);
                } catch (Throwable syncErr) {
                    rawAttemptCf = CompletableFuture.failedFuture(syncErr);
                }
                final CompletableFuture<T> attemptCf = rawAttemptCf;

                inFlightFutures.add(attemptCf);

                attemptCf.whenComplete((result, ex) -> {
                    if (ex == null) {
                        if (completed.compareAndSet(false, true)) {
                            cancelAllTimers(scheduledTimers);
                            int cancelledCount = cancelRemainingAttempts(inFlightFutures, attemptCf);
                            cancelledRedundantCalls.add(cancelledCount);
                            if (currentAttempt == 0) {
                                primaryWins.increment();
                            } else {
                                hedgedWins.increment();
                            }
                            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
                            masterFuture.complete(new HedgedResult<>(
                                    result,
                                    currentAttempt,
                                    hedgesFiredForThisCall.get(),
                                    cancelledCount,
                                    elapsed
                            ));
                        }
                    } else {
                        Throwable unwrapped = unwrapException(ex);
                        if (unwrapped instanceof CancellationException && completed.get()) {
                            return;
                        }
                        errors.add(unwrapped);

                        // Check if exception is non-retriable / non-hedgeable
                        if (!hedgeOnExceptionPredicate.test(unwrapped)) {
                            if (completed.compareAndSet(false, true)) {
                                cancelAllTimers(scheduledTimers);
                                int cancelledCount = cancelRemainingAttempts(inFlightFutures, attemptCf);
                                cancelledRedundantCalls.add(cancelledCount);
                                masterFuture.completeExceptionally(unwrapped);
                            }
                            return;
                        }

                        // Exception is hedgeable: if we have remaining unlaunched hedges, trigger one immediately
                        int failures = failedAttempts.incrementAndGet();
                        if (launchedAttempts.get() < totalAllowedAttempts) {
                            this.run();
                        }
                        // Check if all allowed attempts have now failed
                        if (failures >= totalAllowedAttempts && completed.compareAndSet(false, true)) {
                            cancelAllTimers(scheduledTimers);
                            Throwable primaryErr = errors.isEmpty() ? unwrapped : errors.getFirst();
                            for (int i = 1; i < errors.size(); i++) {
                                if (primaryErr != errors.get(i)) {
                                    primaryErr.addSuppressed(errors.get(i));
                                }
                            }
                            masterFuture.completeExceptionally(primaryErr);
                        }
                    }
                });
            }
        };

        // Schedule speculative hedge timers (1 .. maxHedgedAttempts)
        long delayNanos = hedgeDelay.toNanos();
        for (int h = 1; h <= maxHedgedAttempts; h++) {
            long triggerDelayNanos = delayNanos * h;
            ScheduledFuture<?> timer = scheduler.schedule(
                    launchNextAttempt,
                    triggerDelayNanos,
                    TimeUnit.NANOSECONDS
            );
            scheduledTimers.add(timer);
        }

        // Fire primary request immediately (attempt 0)
        launchNextAttempt.run();

        return masterFuture;
    }

    /**
     * Returns an immutable snapshot of current telemetry metrics.
     */
    public HedgedMetrics metrics() {
        return new HedgedMetrics(
                primaryWins.sum(),
                hedgedWins.sum(),
                totalHedgesFired.sum(),
                cancelledRedundantCalls.sum()
        );
    }

    /**
     * Resets all telemetry counters to zero.
     */
    public void resetMetrics() {
        primaryWins.reset();
        hedgedWins.reset();
        totalHedgesFired.reset();
        cancelledRedundantCalls.reset();
    }

    private static void cancelAllTimers(List<ScheduledFuture<?>> timers) {
        for (ScheduledFuture<?> timer : timers) {
            if (!timer.isDone()) {
                timer.cancel(false);
            }
        }
    }

    private static int cancelRemainingAttempts(List<Future<?>> futures, Future<?> winnerOrExclude) {
        int cancelled = 0;
        for (Future<?> f : futures) {
            if (f != winnerOrExclude && !f.isDone()) {
                if (f.cancel(true)) {
                    cancelled++;
                }
            }
        }
        return cancelled;
    }

    private static Throwable unwrapException(Throwable t) {
        Throwable curr = t;
        while ((curr instanceof CompletionException || curr instanceof ExecutionException) && curr.getCause() != null) {
            curr = curr.getCause();
        }
        return curr;
    }

    @Override
    public void close() {
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
        if (ownsWorkerExecutor) {
            workerExecutor.shutdownNow();
        }
    }

    /**
     * Functional interface for attempt-aware synchronous tasks.
     *
     * @param <T> Return type
     */
    @FunctionalInterface
    public interface AttemptCallable<T> {
        T call(int attemptIndex) throws Exception;
    }

    /**
     * Fluent Builder for {@link HedgedRequestExecutor}.
     */
    public static final class Builder {
        private Duration hedgeDelay = Duration.ofMillis(50);
        private int maxHedgedAttempts = 1;
        private Predicate<Throwable> hedgeOnExceptionPredicate = ex -> true;
        private ExecutorService workerExecutor;
        private ScheduledExecutorService scheduler;

        private Builder() {
        }

        /**
         * Configures the delay before firing each speculative hedged attempt.
         */
        public Builder hedgeDelay(Duration hedgeDelay) {
            Objects.requireNonNull(hedgeDelay, "hedgeDelay must not be null");
            if (hedgeDelay.isNegative() || hedgeDelay.isZero()) {
                throw new IllegalArgumentException("hedgeDelay must be positive, got: " + hedgeDelay);
            }
            this.hedgeDelay = hedgeDelay;
            return this;
        }

        /**
         * Configures the maximum number of secondary speculative hedged requests (in addition to the primary request).
         */
        public Builder maxHedgedAttempts(int maxHedgedAttempts) {
            if (maxHedgedAttempts < 0) {
                throw new IllegalArgumentException("maxHedgedAttempts must be >= 0, got: " + maxHedgedAttempts);
            }
            this.maxHedgedAttempts = maxHedgedAttempts;
            return this;
        }

        /**
         * Configures the exception predicate determining whether an exception from an attempt should trigger
         * or allow hedging (true) or fail the entire execution immediately as a non-retriable fatal error (false).
         */
        public Builder hedgeOnException(Predicate<Throwable> predicate) {
            this.hedgeOnExceptionPredicate = Objects.requireNonNull(predicate, "predicate must not be null");
            return this;
        }

        public Builder workerExecutor(ExecutorService workerExecutor) {
            this.workerExecutor = Objects.requireNonNull(workerExecutor, "workerExecutor must not be null");
            return this;
        }

        public Builder scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
            return this;
        }

        public HedgedRequestExecutor build() {
            return new HedgedRequestExecutor(this);
        }
    }
}
