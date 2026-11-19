package com.msp.crondag;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Directed Acyclic Graph (DAG) Pipeline Trigger and Task Dependency Coordinator.
 * Supports cycle detection, topological sorting, asynchronous non-blocking task execution,
 * and upstream failure propagation.
 */
public class DagPipeline {

    private final Map<String, DagTask<?>> tasks;
    private final ExecutorService executor;
    private final boolean ownsExecutor;

    private DagPipeline(Builder builder) {
        this.tasks = Collections.unmodifiableMap(new LinkedHashMap<>(builder.tasks));
        if (builder.executor != null) {
            this.executor = builder.executor;
            this.ownsExecutor = false;
        } else {
            this.executor = Executors.newCachedThreadPool();
            this.ownsExecutor = true;
        }
        // Validate dependencies and verify DAG has no cycles upon creation
        topologicalSort();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Computes a valid topological execution order (dependencies precede dependent tasks).
     *
     * @return List of task IDs in topological sequence
     * @throws CyclicDependencyException if a circular dependency is detected
     */
    public List<String> topologicalSort() {
        // Validate all dependencies reference existing tasks
        for (DagTask<?> task : tasks.values()) {
            for (String dep : task.getDependencies()) {
                if (!tasks.containsKey(dep)) {
                    throw new IllegalArgumentException("Task '" + task.getId() + "' depends on unknown task '" + dep + "'");
                }
            }
        }

        // 0 = UNVISITED, 1 = VISITING, 2 = VISITED
        Map<String, Integer> visited = new HashMap<>();
        List<String> order = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();

        for (String taskId : tasks.keySet()) {
            if (visited.getOrDefault(taskId, 0) == 0) {
                dfs(taskId, visited, stack, order);
            }
        }

        return Collections.unmodifiableList(order);
    }

    private void dfs(String current, Map<String, Integer> visited, Deque<String> stack, List<String> order) {
        visited.put(current, 1);
        stack.push(current);

        DagTask<?> task = tasks.get(current);
        for (String dep : task.getDependencies()) {
            int state = visited.getOrDefault(dep, 0);
            if (state == 1) {
                // Cycle detected!
                List<String> cyclePath = new ArrayList<>();
                for (String node : stack) {
                    cyclePath.add(node);
                    if (node.equals(dep)) {
                        break;
                    }
                }
                Collections.reverse(cyclePath);
                cyclePath.add(dep);
                throw new CyclicDependencyException(cyclePath);
            } else if (state == 0) {
                dfs(dep, visited, stack, order);
            }
        }

        stack.pop();
        visited.put(current, 2);
        order.add(current);
    }

    /**
     * Executes the DAG pipeline asynchronously.
     *
     * @return CompletableFuture completing with DagExecutionReport
     */
    public CompletableFuture<DagExecutionReport> executeAsync() {
        long pipelineStart = System.nanoTime();
        Map<String, Object> taskOutputs = new ConcurrentHashMap<>();
        Map<String, CompletableFuture<TaskExecutionResult<?>>> futures = new ConcurrentHashMap<>();

        // Create a CompletableFuture placeholder for every task
        for (String taskId : tasks.keySet()) {
            futures.put(taskId, new CompletableFuture<>());
        }

        // Wire task execution once their dependencies complete
        for (DagTask<?> task : tasks.values()) {
            String taskId = task.getId();
            Set<String> deps = task.getDependencies();

            CompletableFuture<?> trigger;
            if (deps.isEmpty()) {
                trigger = CompletableFuture.completedFuture(null);
            } else {
                CompletableFuture<?>[] depFutures = deps.stream()
                        .map(futures::get)
                        .toArray(CompletableFuture[]::new);
                trigger = CompletableFuture.allOf(depFutures);
            }

            trigger.thenRunAsync(() -> {
                // Check if any upstream task failed or was skipped
                for (String depId : deps) {
                    TaskExecutionResult<?> depResult = futures.get(depId).join();
                    if (!depResult.isSuccess()) {
                        UpstreamFailedException ex = new UpstreamFailedException(depId, depResult.error());
                        futures.get(taskId).complete(TaskExecutionResult.skipped(taskId, ex));
                        return;
                    }
                }

                // Execute task
                long taskStart = System.nanoTime();
                try {
                    TaskExecutionContext context = new TaskExecutionContext(taskId, taskOutputs);
                    Object result = task.getAction().execute(context);
                    Duration duration = Duration.ofNanos(System.nanoTime() - taskStart);

                    if (result != null) {
                        taskOutputs.put(taskId, result);
                    }
                    futures.get(taskId).complete(TaskExecutionResult.success(taskId, result, duration));
                } catch (Throwable t) {
                    Duration duration = Duration.ofNanos(System.nanoTime() - taskStart);
                    futures.get(taskId).complete(TaskExecutionResult.failed(taskId, t, duration));
                }
            }, executor);
        }

        // Aggregate all futures into a final report
        CompletableFuture<?>[] all = futures.values().toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(all).thenApply(v -> {
            Duration totalDuration = Duration.ofNanos(System.nanoTime() - pipelineStart);
            Map<String, TaskExecutionResult<?>> results = new LinkedHashMap<>();
            boolean allSuccess = true;

            for (Map.Entry<String, CompletableFuture<TaskExecutionResult<?>>> entry : futures.entrySet()) {
                TaskExecutionResult<?> r = entry.getValue().join();
                results.put(entry.getKey(), r);
                if (!r.isSuccess()) {
                    allSuccess = false;
                }
            }

            if (ownsExecutor) {
                executor.shutdown();
            }

            return new DagExecutionReport(allSuccess, results, totalDuration);
        });
    }

    /**
     * Executes the DAG pipeline synchronously, blocking until all tasks complete.
     *
     * @return Execution report
     */
    public DagExecutionReport execute() {
        try {
            return executeAsync().join();
        } catch (CompletionException ce) {
            if (ce.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw ce;
        }
    }

    public static class Builder {
        private final Map<String, DagTask<?>> tasks = new LinkedHashMap<>();
        private ExecutorService executor;

        public Builder addTask(DagTask<?> task) {
            Objects.requireNonNull(task, "task must not be null");
            if (tasks.containsKey(task.getId())) {
                throw new IllegalArgumentException("Task with id '" + task.getId() + "' is already registered");
            }
            tasks.put(task.getId(), task);
            return this;
        }

        public Builder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        public DagPipeline build() {
            if (tasks.isEmpty()) {
                throw new IllegalStateException("At least one task must be added to the DAG");
            }
            return new DagPipeline(this);
        }
    }
}
