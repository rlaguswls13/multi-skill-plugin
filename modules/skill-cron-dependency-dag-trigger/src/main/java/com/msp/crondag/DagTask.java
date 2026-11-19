package com.msp.crondag;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Definition of an individual task node in the DAG pipeline.
 *
 * @param <T> Output value type produced by the task
 */
public class DagTask<T> {

    private final String id;
    private final Set<String> dependencies;
    private final TaskAction<T> action;
    private final Duration timeout;

    public DagTask(String id, Set<String> dependencies, TaskAction<T> action, Duration timeout) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.dependencies = dependencies != null ? Collections.unmodifiableSet(new HashSet<>(dependencies)) : Set.of();
        this.timeout = timeout;
    }

    public static <T> DagTask<T> of(String id, TaskAction<T> action) {
        return new DagTask<>(id, Set.of(), action, null);
    }

    public static <T> DagTask<T> of(String id, Set<String> dependencies, TaskAction<T> action) {
        return new DagTask<>(id, dependencies, action, null);
    }

    public static <T> DagTask<T> of(String id, Set<String> dependencies, Duration timeout, TaskAction<T> action) {
        return new DagTask<>(id, dependencies, action, timeout);
    }

    public static <T> DagTask<T> callable(String id, Set<String> dependencies, Callable<T> callable) {
        Objects.requireNonNull(callable, "callable must not be null");
        return new DagTask<>(id, dependencies, ctx -> callable.call(), null);
    }

    public static DagTask<Void> runnable(String id, Set<String> dependencies, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        return new DagTask<>(id, dependencies, ctx -> {
            runnable.run();
            return null;
        }, null);
    }

    public String getId() {
        return id;
    }

    public Set<String> getDependencies() {
        return dependencies;
    }

    public TaskAction<T> getAction() {
        return action;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
