package com.msp.fanout;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Parallel task distributor (Fan-out) and aggregator (Fan-in) orchestrator
 * leveraging Java 21 Virtual Threads ({@link Thread#ofVirtual()}).
 *
 * @param <T> result type of tasks
 */
public final class FanOutOrchestrator<T> {

    private final List<Callable<T>> tasks;
    private Duration timeout;
    private String namePrefix = "fanout-vthread-";
    private boolean failFast = false;

    private FanOutOrchestrator(List<Callable<T>> tasks) {
        this.tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks must not be null"));
    }

    /**
     * Initializes a fan-out operation from a list of callables.
     *
     * @param tasks list of tasks to execute concurrently
     * @param <T>   result type
     * @return orchestrator builder
     */
    public static <T> FanOutOrchestrator<T> fanOut(Collection<? extends Callable<T>> tasks) {
        Objects.requireNonNull(tasks, "tasks collection must not be null");
        List<Callable<T>> list = new ArrayList<>(tasks);
        return new FanOutOrchestrator<>(list);
    }

    /**
     * Initializes a fan-out operation from varargs of callables.
     *
     * @param tasks varargs tasks
     * @param <T>   result type
     * @return orchestrator builder
     */
    @SafeVarargs
    public static <T> FanOutOrchestrator<T> fanOut(Callable<T>... tasks) {
        Objects.requireNonNull(tasks, "tasks array must not be null");
        return new FanOutOrchestrator<>(Arrays.asList(tasks));
    }

    /**
     * Sets an overall execution timeout deadline. If tasks exceed this duration,
     * remaining tasks are cancelled and recorded as {@link TimeoutException}.
     *
     * @param timeout max duration to wait
     * @return this orchestrator
     */
    public FanOutOrchestrator<T> timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Sets the virtual thread name prefix.
     *
     * @param namePrefix prefix for thread names
     * @return this orchestrator
     */
    public FanOutOrchestrator<T> namePrefix(String namePrefix) {
        this.namePrefix = (namePrefix != null && !namePrefix.isBlank()) ? namePrefix : "fanout-vthread-";
        return this;
    }

    /**
     * Enables or disables fail-fast mode. When enabled, upon the first task failure,
     * remaining tasks are immediately cancelled.
     *
     * @param failFast true to abort remaining tasks upon first failure
     * @return this orchestrator
     */
    public FanOutOrchestrator<T> failFast(boolean failFast) {
        this.failFast = failFast;
        return this;
    }

    /**
     * Executes all tasks concurrently across Java 21 virtual threads,
     * joins results, and aggregates outcomes into a {@link FanInResult}.
     *
     * @return aggregated result containing successes, failures, and execution duration
     */
    public FanInResult<T> execute() {
        if (tasks.isEmpty()) {
            return new FanInResult<>(List.of(), Duration.ZERO);
        }

        long startNanos = System.nanoTime();
        ThreadFactory threadFactory = Thread.ofVirtual().name(namePrefix, 0).factory();

        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(threadFactory)) {
            List<Future<TaskResult<T>>> futures = new ArrayList<>(tasks.size());

            for (int i = 0; i < tasks.size(); i++) {
                final int index = i;
                final Callable<T> callable = tasks.get(i);
                futures.add(executor.submit(() -> {
                    try {
                        T val = (callable != null) ? callable.call() : null;
                        return TaskResult.success(index, val);
                    } catch (Throwable t) {
                        return TaskResult.failure(index, t);
                    }
                }));
            }

            Long deadlineNanos = (timeout != null && !timeout.isZero() && !timeout.isNegative())
                    ? startNanos + timeout.toNanos()
                    : null;

            List<TaskResult<T>> results = new ArrayList<>(tasks.size());

            for (int i = 0; i < futures.size(); i++) {
                Future<TaskResult<T>> future = futures.get(i);
                try {
                    TaskResult<T> result;
                    if (deadlineNanos != null) {
                        long remainingNanos = deadlineNanos - System.nanoTime();
                        if (remainingNanos <= 0) {
                            future.cancel(true);
                            result = TaskResult.failure(i, new TimeoutException("Task exceeded timeout deadline of " + timeout));
                        } else {
                            result = future.get(remainingNanos, TimeUnit.NANOSECONDS);
                        }
                    } else {
                        result = future.get();
                    }

                    results.add(result);

                    if (failFast && !result.isSuccess()) {
                        for (int j = i + 1; j < futures.size(); j++) {
                            futures.get(j).cancel(true);
                        }
                    }
                } catch (TimeoutException e) {
                    future.cancel(true);
                    results.add(TaskResult.failure(i, e));
                    if (deadlineNanos != null) {
                        for (int j = i + 1; j < futures.size(); j++) {
                            Future<TaskResult<T>> rem = futures.get(j);
                            if (rem.isDone()) {
                                try {
                                    results.add(rem.get());
                                } catch (Exception ex) {
                                    results.add(TaskResult.failure(j, ex));
                                }
                            } else {
                                rem.cancel(true);
                                results.add(TaskResult.failure(j, new TimeoutException("Task timed out after " + timeout)));
                            }
                        }
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.cancel(true);
                    results.add(TaskResult.failure(i, e));
                    for (int j = i + 1; j < futures.size(); j++) {
                        futures.get(j).cancel(true);
                        results.add(TaskResult.failure(j, new InterruptedException("Task cancelled due to thread interrupt")));
                    }
                    break;
                } catch (ExecutionException e) {
                    results.add(TaskResult.failure(i, e.getCause()));
                } catch (CancellationException e) {
                    results.add(TaskResult.failure(i, e));
                }
            }

            long elapsedNanos = System.nanoTime() - startNanos;
            return new FanInResult<>(results, Duration.ofNanos(elapsedNanos));
        }
    }
}
