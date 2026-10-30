package com.msp.cronoverlap;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 스케줄러 배치 작업의 수행 시간이 Cron 주기보다 길어질 때,
 * 이전 작업이 종료되지 않았으면 다음 실행을 안전하게 스킵(Skip)하는 비재진입(Non-reentrant) 실행 가드.
 */
public final class CronOverlapGuard {

    private final String taskName;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong executedCount = new AtomicLong(0);
    private final AtomicLong skippedCount = new AtomicLong(0);

    public CronOverlapGuard(String taskName) {
        this.taskName = Objects.requireNonNull(taskName, "taskName must not be null");
    }

    public static CronOverlapGuard of(String taskName) {
        return new CronOverlapGuard(taskName);
    }

    public ExecutionResult<Void> run(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        return call(() -> {
            task.run();
            return null;
        });
    }

    public <T> ExecutionResult<T> call(Callable<T> task) {
        Objects.requireNonNull(task, "task must not be null");

        // Try to acquire execution token
        if (!running.compareAndSet(false, true)) {
            skippedCount.incrementAndGet();
            return new ExecutionResult<>(ExecutionStatus.SKIPPED_ALREADY_RUNNING, null, Duration.ZERO, null);
        }

        Instant start = Instant.now();
        T value = null;
        Throwable error = null;

        try {
            value = task.call();
            executedCount.incrementAndGet();
        } catch (Throwable t) {
            error = t;
            executedCount.incrementAndGet();
        } finally {
            running.set(false);
        }

        Duration duration = Duration.between(start, Instant.now());
        return new ExecutionResult<>(ExecutionStatus.EXECUTED, value, duration, error);
    }

    public boolean isRunning() {
        return running.get();
    }

    public long executedCount() {
        return executedCount.get();
    }

    public long skippedCount() {
        return skippedCount.get();
    }

    public String taskName() {
        return taskName;
    }
}
