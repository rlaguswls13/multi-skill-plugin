package com.msp.health;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes registered health checks with timeout enforcement and composite status aggregation.
 */
public class HealthChecker implements AutoCloseable {

    private final HealthRegistry registry;
    private final ExecutorService executor;
    private final boolean ownsExecutor;

    public HealthChecker() {
        this(new HealthRegistry());
    }

    public HealthChecker(HealthRegistry registry) {
        this(registry, Executors.newVirtualThreadPerTaskExecutor(), true);
    }

    public HealthChecker(HealthRegistry registry, ExecutorService executor) {
        this(registry, executor, false);
    }

    private HealthChecker(HealthRegistry registry, ExecutorService executor, boolean ownsExecutor) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.ownsExecutor = ownsExecutor;
    }

    public HealthRegistry getRegistry() {
        return registry;
    }

    /**
     * Executes all registered health checks and returns an aggregated HealthReport.
     */
    public HealthReport checkAll() {
        Instant startTime = Instant.now();
        long startNanos = System.nanoTime();

        Map<String, HealthRegistry.Registration> allChecks = registry.getAll();
        Map<String, HealthResult> results = new LinkedHashMap<>();

        if (allChecks.isEmpty()) {
            return new HealthReport(HealthStatus.UP, results, Duration.ZERO, startTime);
        }

        // Run checks in parallel via executor
        Map<String, CompletableFuture<HealthResult>> futures = new LinkedHashMap<>();
        for (Map.Entry<String, HealthRegistry.Registration> entry : allChecks.entrySet()) {
            String name = entry.getKey();
            HealthRegistry.Registration reg = entry.getValue();
            futures.put(name, CompletableFuture.supplyAsync(() -> executeCheck(reg), executor));
        }

        HealthStatus composite = HealthStatus.UP;
        for (Map.Entry<String, CompletableFuture<HealthResult>> entry : futures.entrySet()) {
            String name = entry.getKey();
            HealthResult result;
            try {
                result = entry.getValue().join();
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                result = HealthResult.down("Execution failed: " + cause.getMessage(), cause);
            }
            results.put(name, result);
            composite = composite.combine(result.status());
        }

        long totalNanos = System.nanoTime() - startNanos;
        return new HealthReport(composite, results, Duration.ofNanos(totalNanos), startTime);
    }

    /**
     * Executes a single named health check.
     */
    public HealthResult checkOne(String name) {
        HealthRegistry.Registration reg = registry.get(name)
                .orElseThrow(() -> new IllegalArgumentException("No health check registered with name: " + name));
        return executeCheck(reg);
    }

    /**
     * Runs checkAll and returns JSON string.
     */
    public String checkAllJson(boolean pretty) {
        return checkAll().toJson(pretty);
    }

    private HealthResult executeCheck(HealthRegistry.Registration reg) {
        long startNanos = System.nanoTime();
        CompletableFuture<HealthResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                HealthResult res = reg.check().check();
                if (res == null) {
                    return HealthResult.unknown("Check returned null result");
                }
                long elapsedNanos = System.nanoTime() - startNanos;
                return new HealthResult(res.status(), res.message(), res.details(), Duration.ofNanos(elapsedNanos), Instant.now());
            } catch (Throwable t) {
                long elapsedNanos = System.nanoTime() - startNanos;
                return HealthResult.down("Check failed with exception: " + t.getMessage(), t);
            }
        }, executor);

        long timeoutMs = reg.timeout().toMillis();
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            long elapsedNanos = System.nanoTime() - startNanos;
            Map<String, Object> details = Map.of(
                    "timeoutLimitMs", timeoutMs,
                    "timedOut", true
            );
            return HealthResult.down(
                    "Health check timed out after " + timeoutMs + "ms",
                    details,
                    Duration.ofNanos(elapsedNanos)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HealthResult.down("Health check interrupted", e);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return HealthResult.down("Health check execution error: " + cause.getMessage(), cause);
        }
    }

    @Override
    public void close() {
        if (ownsExecutor) {
            executor.shutdown();
        }
    }
}
