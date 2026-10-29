package com.msp.shutdown;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JVM shutdown hook coordinator with ordered component cleanup and timeout safety.
 * <p>
 * Ensures ordered teardown of components (e.g. reverse dependency order:
 * Order 1: Web listeners -&gt; Order 2: In-flight workers -&gt; Order 3: Databases &amp; Caches).
 * Enforces overall timeout deadlines, captures component exceptions without halting teardown,
 * and executes components in the same order phase concurrently using Virtual Threads.
 * </p>
 */
public class GracefulShutdownManager {

    private record RegisteredComponent(String name, int order, AutoCloseable closeable) {}

    private final Map<String, RegisteredComponent> components = new ConcurrentHashMap<>();
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);
    private final CompletableFuture<ShutdownReport> shutdownFuture = new CompletableFuture<>();
    private Thread shutdownHookThread;

    /**
     * Registers an {@link AutoCloseable} component with an execution order priority.
     * Components with lower order numbers shut down first (Phase 1, Phase 2, etc.).
     *
     * @param name      unique name of the component
     * @param order     shutdown order phase (e.g. 1, 2, 3)
     * @param closeable closeable cleanup handler
     */
    public void registerComponent(String name, int order, AutoCloseable closeable) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(closeable, "closeable must not be null");
        checkNotShutdown();
        components.put(name, new RegisteredComponent(name, order, closeable));
    }

    /**
     * Registers a {@link Runnable} cleanup action with an execution order priority.
     *
     * @param name   unique name of the component
     * @param order  shutdown order phase
     * @param action runnable cleanup action
     */
    public void registerAction(String name, int order, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        registerComponent(name, order, (AutoCloseable) action::run);
    }

    /**
     * Deregisters a component by name.
     *
     * @param name component name
     * @return true if removed, false if not found
     */
    public boolean deregisterComponent(String name) {
        checkNotShutdown();
        return components.remove(name) != null;
    }

    /**
     * Adds a JVM shutdown hook using a default 10-second timeout deadline.
     *
     * @return true if hook was added, false if already registered
     */
    public synchronized boolean addShutdownHook() {
        return addShutdownHook(Duration.ofSeconds(10));
    }

    /**
     * Adds a JVM shutdown hook with specified timeout deadline.
     *
     * @param timeout maximum duration to wait for cleanup during JVM shutdown
     * @return true if hook was added, false if already registered
     */
    public synchronized boolean addShutdownHook(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (shutdownHookThread != null) {
            return false;
        }

        shutdownHookThread = new Thread(() -> {
            try {
                initiateShutdown(timeout);
            } catch (Throwable t) {
                System.err.println("Error during graceful shutdown: " + t.getMessage());
            }
        }, "graceful-shutdown-hook");

        try {
            Runtime.getRuntime().addShutdownHook(shutdownHookThread);
            return true;
        } catch (IllegalStateException e) {
            // JVM already shutting down
            shutdownHookThread = null;
            return false;
        }
    }

    /**
     * Safely unregisters the JVM shutdown hook.
     *
     * @return true if hook was removed
     */
    public synchronized boolean removeShutdownHook() {
        if (shutdownHookThread == null) {
            return false;
        }
        try {
            boolean removed = Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
            shutdownHookThread = null;
            return removed;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * Initiates ordered graceful shutdown of all registered components with a timeout deadline.
     * This call is idempotent: subsequent invocations will wait for the ongoing shutdown.
     *
     * @param timeout maximum duration to complete shutdown
     * @return final shutdown report
     */
    public ShutdownReport initiateShutdown(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");

        if (shutdownInitiated.compareAndSet(false, true)) {
            ShutdownReport report = executeShutdown(timeout);
            shutdownFuture.complete(report);
            return report;
        } else {
            try {
                return shutdownFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                return new ShutdownReport(true, timeout, List.of());
            }
        }
    }

    /**
     * Returns true if shutdown has been initiated.
     */
    public boolean isShutdownInitiated() {
        return shutdownInitiated.get();
    }

    /**
     * Returns true if shutdown has completed.
     */
    public boolean isShutdownCompleted() {
        return shutdownFuture.isDone();
    }

    /**
     * Returns the count of registered components.
     */
    public int registeredCount() {
        return components.size();
    }

    /**
     * Clears all registered components (useful for test resets).
     */
    public void clear() {
        checkNotShutdown();
        components.clear();
    }

    private ShutdownReport executeShutdown(Duration timeout) {
        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + timeout.toNanos();
        boolean overallTimedOut = false;

        // Group components by order, sorted ascending
        List<RegisteredComponent> allComponents = new ArrayList<>(components.values());
        Map<Integer, List<RegisteredComponent>> phaseMap = new TreeMap<>();
        for (RegisteredComponent comp : allComponents) {
            phaseMap.computeIfAbsent(comp.order(), k -> new ArrayList<>()).add(comp);
        }

        List<ComponentShutdownResult> results = new ArrayList<>(allComponents.size());

        for (Map.Entry<Integer, List<RegisteredComponent>> entry : phaseMap.entrySet()) {
            int order = entry.getKey();
            List<RegisteredComponent> phaseComponents = entry.getValue();

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                overallTimedOut = true;
                for (RegisteredComponent comp : phaseComponents) {
                    results.add(ComponentShutdownResult.failure(
                            comp.name(), order, Duration.ZERO,
                            new TimeoutException("Shutdown deadline of " + timeout + " expired before order phase " + order)
                    ));
                }
                continue;
            }

            // Execute phase components in parallel using Virtual Threads
            try (var executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("shutdown-phase-" + order + "-", 0).factory())) {
                List<Future<ComponentShutdownResult>> futures = new ArrayList<>(phaseComponents.size());

                for (RegisteredComponent comp : phaseComponents) {
                    futures.add(executor.submit(() -> {
                        long compStart = System.nanoTime();
                        try {
                            comp.closeable().close();
                            Duration dur = Duration.ofNanos(System.nanoTime() - compStart);
                            return ComponentShutdownResult.success(comp.name(), order, dur);
                        } catch (Throwable t) {
                            Duration dur = Duration.ofNanos(System.nanoTime() - compStart);
                            return ComponentShutdownResult.failure(comp.name(), order, dur, t);
                        }
                    }));
                }

                for (int i = 0; i < futures.size(); i++) {
                    Future<ComponentShutdownResult> future = futures.get(i);
                    RegisteredComponent comp = phaseComponents.get(i);
                    long rem = deadlineNanos - System.nanoTime();

                    if (rem <= 0) {
                        overallTimedOut = true;
                        future.cancel(true);
                        results.add(ComponentShutdownResult.failure(
                                comp.name(), order, Duration.ZERO,
                                new TimeoutException("Component exceeded shutdown deadline of " + timeout)
                        ));
                    } else {
                        try {
                            ComponentShutdownResult res = future.get(rem, TimeUnit.NANOSECONDS);
                            results.add(res);
                        } catch (TimeoutException e) {
                            overallTimedOut = true;
                            future.cancel(true);
                            results.add(ComponentShutdownResult.failure(
                                    comp.name(), order, Duration.ofNanos(System.nanoTime() - startNanos), e
                            ));
                        } catch (Exception e) {
                            results.add(ComponentShutdownResult.failure(
                                    comp.name(), order, Duration.ofNanos(System.nanoTime() - startNanos), e
                            ));
                        }
                    }
                }
            }
        }

        Duration totalDuration = Duration.ofNanos(System.nanoTime() - startNanos);
        return new ShutdownReport(overallTimedOut, totalDuration, results);
    }

    private void checkNotShutdown() {
        if (shutdownInitiated.get()) {
            throw new IllegalStateException("Shutdown has already been initiated");
        }
    }
}
