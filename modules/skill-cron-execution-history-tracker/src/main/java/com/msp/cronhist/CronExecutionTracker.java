package com.msp.cronhist;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe Cron Execution History, Drift Detection, and Performance Anomaly Tracker.
 */
public final class CronExecutionTracker {

    public static final int DEFAULT_HISTORY_CAPACITY = 200;

    private final int historyCapacity;
    private final ConcurrentHashMap<String, TaskHistory> taskHistories = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AnomalyPolicy> taskPolicies = new ConcurrentHashMap<>();
    private final List<Consumer<AnomalyEvent>> anomalyListeners = new CopyOnWriteArrayList<>();
    private AnomalyPolicy defaultPolicy = AnomalyPolicy.standard();

    public CronExecutionTracker() {
        this(DEFAULT_HISTORY_CAPACITY);
    }

    public CronExecutionTracker(int historyCapacity) {
        if (historyCapacity <= 0) {
            throw new IllegalArgumentException("historyCapacity must be positive");
        }
        this.historyCapacity = historyCapacity;
    }

    public CronExecutionTracker setDefaultPolicy(AnomalyPolicy policy) {
        this.defaultPolicy = Objects.requireNonNull(policy, "policy cannot be null");
        return this;
    }

    public CronExecutionTracker setTaskPolicy(String taskId, AnomalyPolicy policy) {
        Objects.requireNonNull(taskId, "taskId cannot be null");
        Objects.requireNonNull(policy, "policy cannot be null");
        taskPolicies.put(taskId, policy);
        return this;
    }

    public CronExecutionTracker addAnomalyListener(Consumer<AnomalyEvent> listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        anomalyListeners.add(listener);
        return this;
    }

    /**
     * Records an execution instance.
     */
    public void recordExecution(String taskId, Instant scheduledTime, Instant actualStartTime, Instant finishTime, boolean success) {
        recordExecution(ExecutionRecord.of(taskId, scheduledTime, actualStartTime, finishTime, success));
    }

    /**
     * Records an execution instance and performs anomaly checks.
     */
    public void recordExecution(ExecutionRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        String taskId = record.taskId();

        TaskHistory history = taskHistories.computeIfAbsent(taskId, k -> new TaskHistory(historyCapacity));
        history.add(record);

        // Evaluate anomalies
        AnomalyPolicy policy = taskPolicies.getOrDefault(taskId, defaultPolicy);
        checkAnomaliesForRecord(record, history, policy);
    }

    private void checkAnomaliesForRecord(ExecutionRecord record, TaskHistory history, AnomalyPolicy policy) {
        List<AnomalyEvent> detected = new ArrayList<>();
        Instant now = Instant.now();

        // 1. Severe Drift Detection
        if (policy.maxAllowedDrift() != null) {
            Duration drift = record.drift();
            if (drift.compareTo(policy.maxAllowedDrift()) > 0) {
                detected.add(new AnomalyEvent(
                        AnomalyType.SEVERE_DRIFT,
                        record.taskId(),
                        String.format("Severe schedule drift detected: %d ms (threshold: %d ms)",
                                drift.toMillis(), policy.maxAllowedDrift().toMillis()),
                        now,
                        record
                ));
            }
        }

        // 2. Execution Overrun Detection
        Duration duration = record.duration();
        Duration overrunLimit = policy.expectedInterval();
        if (policy.maxAllowedDuration() != null) {
            overrunLimit = policy.maxAllowedDuration();
        }
        if (overrunLimit != null && duration.compareTo(overrunLimit) > 0) {
            detected.add(new AnomalyEvent(
                    AnomalyType.EXECUTION_OVERRUN,
                    record.taskId(),
                    String.format("Execution overrun detected: %d ms exceeds limit of %d ms, risking overlapping runs",
                            duration.toMillis(), overrunLimit.toMillis()),
                    now,
                    record
            ));
        }

        // 3. Failure Spike Detection
        if (policy.consecutiveFailureThreshold() > 0) {
            int consecutiveFailures = history.consecutiveFailures();
            if (consecutiveFailures >= policy.consecutiveFailureThreshold()) {
                detected.add(new AnomalyEvent(
                        AnomalyType.FAILURE_SPIKE,
                        record.taskId(),
                        String.format("Failure spike detected: %d consecutive failures (threshold: %d)",
                                consecutiveFailures, policy.consecutiveFailureThreshold()),
                        now,
                        record
                ));
            }
        }

        for (AnomalyEvent event : detected) {
            history.addAnomaly(event);
            for (Consumer<AnomalyEvent> listener : anomalyListeners) {
                try {
                    listener.accept(event);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Computes performance statistics across all retained executions for a task.
     */
    public Optional<ExecutionStatistics> getStatistics(String taskId) {
        if (taskId == null) return Optional.empty();
        TaskHistory history = taskHistories.get(taskId);
        if (history == null) return Optional.empty();
        List<ExecutionRecord> records = history.getRecords();
        if (records.isEmpty()) return Optional.empty();
        return Optional.of(ExecutionStatistics.fromRecords(taskId, records));
    }

    /**
     * Retrieves recent execution records for a task up to the limit.
     */
    public List<ExecutionRecord> getHistory(String taskId, int limit) {
        if (taskId == null || limit <= 0) return Collections.emptyList();
        TaskHistory history = taskHistories.get(taskId);
        if (history == null) return Collections.emptyList();
        return history.getRecentRecords(limit);
    }

    /**
     * Retrieves recent detected anomaly events for a task.
     */
    public List<AnomalyEvent> getAnomalies(String taskId) {
        if (taskId == null) return Collections.emptyList();
        TaskHistory history = taskHistories.get(taskId);
        if (history == null) return Collections.emptyList();
        return history.getAnomalies();
    }

    public void clear(String taskId) {
        if (taskId != null) {
            taskHistories.remove(taskId);
        }
    }

    public void clearAll() {
        taskHistories.clear();
    }

    private static final class TaskHistory {
        private final int capacity;
        private final LinkedList<ExecutionRecord> records = new LinkedList<>();
        private final List<AnomalyEvent> anomalies = new ArrayList<>();
        private int consecutiveFailures = 0;

        TaskHistory(int capacity) {
            this.capacity = capacity;
        }

        synchronized void add(ExecutionRecord record) {
            if (record.success()) {
                consecutiveFailures = 0;
            } else {
                consecutiveFailures++;
            }

            records.addLast(record);
            if (records.size() > capacity) {
                records.removeFirst();
            }
        }

        synchronized void addAnomaly(AnomalyEvent anomaly) {
            anomalies.add(anomaly);
            if (anomalies.size() > capacity) {
                anomalies.remove(0);
            }
        }

        synchronized int consecutiveFailures() {
            return consecutiveFailures;
        }

        synchronized List<ExecutionRecord> getRecords() {
            return new ArrayList<>(records);
        }

        synchronized List<ExecutionRecord> getRecentRecords(int limit) {
            int size = records.size();
            int fromIndex = Math.max(0, size - limit);
            return new ArrayList<>(records.subList(fromIndex, size));
        }

        synchronized List<AnomalyEvent> getAnomalies() {
            return new ArrayList<>(anomalies);
        }
    }
}
