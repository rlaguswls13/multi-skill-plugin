package com.msp.fanout;

import java.time.Duration;
import java.util.*;

/**
 * Aggregated result produced by a {@link FanOutOrchestrator} execution.
 *
 * @param <T> result value type
 */
public class FanInResult<T> {

    private final List<TaskResult<T>> allResults;
    private final Duration executionDuration;
    private final List<T> successfulResults;
    private final List<Throwable> failures;
    private final Map<Integer, Throwable> failureMap;

    public FanInResult(List<TaskResult<T>> allResults, Duration executionDuration) {
        this.allResults = List.copyOf(Objects.requireNonNull(allResults, "allResults must not be null"));
        this.executionDuration = Objects.requireNonNull(executionDuration, "executionDuration must not be null");

        List<T> successes = new ArrayList<>();
        List<Throwable> errs = new ArrayList<>();
        Map<Integer, Throwable> errMap = new LinkedHashMap<>();

        for (TaskResult<T> res : this.allResults) {
            if (res.isSuccess()) {
                successes.add(res.value());
            } else {
                if (res.error() != null) {
                    errs.add(res.error());
                    errMap.put(res.index(), res.error());
                }
            }
        }

        this.successfulResults = Collections.unmodifiableList(successes);
        this.failures = Collections.unmodifiableList(errs);
        this.failureMap = Collections.unmodifiableMap(errMap);
    }

    /**
     * Returns the list of successfully computed values.
     */
    public List<T> successfulResults() {
        return successfulResults;
    }

    /**
     * Returns the list of exceptions thrown by failed tasks.
     */
    public List<Throwable> failures() {
        return failures;
    }

    /**
     * Returns a map of task index to the exception thrown.
     */
    public Map<Integer, Throwable> failureMap() {
        return failureMap;
    }

    /**
     * Returns all task outcomes (both successful and failed) in submitted order.
     */
    public List<TaskResult<T>> allResults() {
        return allResults;
    }

    /**
     * Total elapsed wall-clock duration of the fan-out / fan-in execution.
     */
    public Duration executionDuration() {
        return executionDuration;
    }

    /**
     * Total number of distributed tasks.
     */
    public int totalTasks() {
        return allResults.size();
    }

    /**
     * Number of successfully completed tasks.
     */
    public int successCount() {
        return successfulResults.size();
    }

    /**
     * Number of failed or timed-out tasks.
     */
    public int failureCount() {
        return failures.size();
    }

    /**
     * Returns true if all tasks succeeded with no failures or timeouts.
     */
    public boolean isAllSuccessful() {
        return failureCount() == 0;
    }

    /**
     * Returns true if at least one task failed or timed out.
     */
    public boolean hasFailures() {
        return failureCount() > 0;
    }

    @Override
    public String toString() {
        return "FanInResult{total=" + totalTasks() +
                ", successes=" + successCount() +
                ", failures=" + failureCount() +
                ", duration=" + executionDuration.toMillis() + "ms}";
    }
}
