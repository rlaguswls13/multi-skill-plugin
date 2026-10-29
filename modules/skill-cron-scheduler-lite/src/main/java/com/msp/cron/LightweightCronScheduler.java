package com.msp.cron;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, lightweight in-memory cron scheduler driven by {@link ScheduledExecutorService}.
 */
public final class LightweightCronScheduler implements AutoCloseable {

    private final ScheduledExecutorService executor;
    private final ZoneId zoneId;
    private final Map<String, ScheduledTaskImpl> tasks = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    public LightweightCronScheduler() {
        this(Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
            Thread t = new Thread(r, "cron-scheduler-worker");
            t.setDaemon(true);
            return t;
        }), ZoneId.systemDefault());
    }

    public LightweightCronScheduler(ScheduledExecutorService executor, ZoneId zoneId) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
    }

    public static LightweightCronScheduler create() {
        return new LightweightCronScheduler();
    }

    public static LightweightCronScheduler create(ZoneId zoneId) {
        return new LightweightCronScheduler(Executors.newScheduledThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()), r -> {
                    Thread t = new Thread(r, "cron-scheduler-worker");
                    t.setDaemon(true);
                    return t;
                }), zoneId);
    }

    /**
     * Schedules a task using a cron expression string.
     */
    public ScheduledTask schedule(String cronExpression, Runnable task) {
        return schedule(null, CronExpression.parse(cronExpression), task);
    }

    /**
     * Schedules a task using a parsed {@link CronExpression}.
     */
    public ScheduledTask schedule(CronExpression cron, Runnable task) {
        return schedule(null, cron, task);
    }

    /**
     * Schedules a named task using a {@link CronExpression}.
     */
    public ScheduledTask schedule(String name, CronExpression cron, Runnable task) {
        Objects.requireNonNull(cron, "cron must not be null");
        Objects.requireNonNull(task, "task must not be null");

        String taskId = "task-" + idGenerator.incrementAndGet();
        String taskName = (name != null && !name.isBlank()) ? name : taskId;

        ScheduledTaskImpl scheduledTask = new ScheduledTaskImpl(taskId, taskName, cron, task);
        tasks.put(taskId, scheduledTask);
        scheduledTask.scheduleNext();
        return scheduledTask;
    }

    /**
     * Cancels a scheduled task by ID.
     */
    public void cancel(String taskId) {
        ScheduledTaskImpl task = tasks.remove(taskId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Gets a scheduled task by ID.
     */
    public Optional<ScheduledTask> getTask(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /**
     * Returns an unmodifiable list of active scheduled tasks.
     */
    public List<ScheduledTask> activeTasks() {
        return List.copyOf(tasks.values());
    }

    /**
     * Gracefully shuts down the scheduler.
     */
    public void shutdown() {
        tasks.values().forEach(ScheduledTaskImpl::cancel);
        tasks.clear();
        executor.shutdown();
    }

    /**
     * Immediately shuts down the scheduler.
     */
    public void shutdownNow() {
        tasks.values().forEach(ScheduledTaskImpl::cancel);
        tasks.clear();
        executor.shutdownNow();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public void close() {
        shutdown();
    }

    private final class ScheduledTaskImpl implements ScheduledTask {
        private final String taskId;
        private final String name;
        private final CronExpression expression;
        private final Runnable action;
        private volatile ScheduledFuture<?> currentFuture;
        private volatile ZonedDateTime nextExecution;
        private volatile boolean cancelled = false;

        ScheduledTaskImpl(String taskId, String name, CronExpression expression, Runnable action) {
            this.taskId = taskId;
            this.name = name;
            this.expression = expression;
            this.action = action;
        }

        synchronized void scheduleNext() {
            if (cancelled || isShutdown()) {
                return;
            }

            ZonedDateTime now = ZonedDateTime.now(zoneId);
            try {
                this.nextExecution = expression.next(now);
            } catch (CronException e) {
                cancel();
                return;
            }

            long delayMillis = Duration.between(now, nextExecution).toMillis();
            if (delayMillis < 0) {
                delayMillis = 0;
            }

            try {
                this.currentFuture = executor.schedule(this::execute, delayMillis, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                cancel();
            }
        }

        private void execute() {
            if (cancelled || isShutdown()) {
                return;
            }
            try {
                action.run();
            } catch (Throwable ignored) {
                // Prevent task exceptions from killing scheduler loop
            } finally {
                scheduleNext();
            }
        }

        @Override
        public String taskId() {
            return taskId;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CronExpression expression() {
            return expression;
        }

        @Override
        public ZonedDateTime nextExecutionTime() {
            return nextExecution;
        }

        @Override
        public synchronized void cancel() {
            this.cancelled = true;
            this.nextExecution = null;
            if (currentFuture != null) {
                currentFuture.cancel(false);
            }
            tasks.remove(taskId);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
