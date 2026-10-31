package com.msp.processpipe;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Deadlock-free multi-process asynchronous pipeline runner connecting stdout -> stdin across stages.
 * Pure Java 21 standard library implementation with zero 3rd-party dependencies.
 */
public final class ProcessPipeline {

    public static final int DEFAULT_MAX_OUTPUT_BYTES = 5 * 1024 * 1024; // 5 MB per stream

    private final List<PipelineStage> stages = new ArrayList<>();
    private final Map<String, String> defaultEnvironment = new HashMap<>();
    private File defaultWorkingDir = null;
    private int maxOutputBytes = DEFAULT_MAX_OUTPUT_BYTES;

    private ProcessPipeline() {
        // Private constructor, use create()
    }

    /**
     * Creates a new fluent ProcessPipeline instance.
     */
    public static ProcessPipeline create() {
        return new ProcessPipeline();
    }

    /**
     * Appends a pipeline stage given command arguments.
     */
    public ProcessPipeline stage(String... command) {
        return stage(PipelineStage.of(command));
    }

    /**
     * Appends a pipeline stage given a command list.
     */
    public ProcessPipeline stage(List<String> command) {
        return stage(PipelineStage.of(command));
    }

    /**
     * Appends an existing PipelineStage to the pipeline.
     */
    public ProcessPipeline stage(PipelineStage stage) {
        Objects.requireNonNull(stage, "stage must not be null");
        stages.add(stage);
        return this;
    }

    /**
     * Sets the default working directory for stages that do not specify one.
     */
    public ProcessPipeline workingDir(File workingDir) {
        this.defaultWorkingDir = workingDir;
        return this;
    }

    /**
     * Sets global environment variables applied to all pipeline stages.
     */
    public ProcessPipeline environment(Map<String, String> env) {
        if (env != null) {
            this.defaultEnvironment.putAll(env);
        }
        return this;
    }

    /**
     * Sets the maximum bytes captured per stream (default: 5MB).
     */
    public ProcessPipeline maxOutputBytes(int maxOutputBytes) {
        if (maxOutputBytes <= 0) {
            throw new IllegalArgumentException("maxOutputBytes must be positive: " + maxOutputBytes);
        }
        this.maxOutputBytes = maxOutputBytes;
        return this;
    }

    /**
     * Executes the configured multi-process pipeline with the specified timeout.
     * Connects stdout of each stage to stdin of the subsequent stage, drains stderr asynchronously
     * to eliminate pipe deadlocks, and terminates the entire process tree on timeout.
     *
     * @param timeout maximum execution duration
     * @return consolidated PipelineResult
     */
    public PipelineResult execute(Duration timeout) {
        if (stages.isEmpty()) {
            throw new IllegalStateException("Process pipeline must contain at least one stage");
        }
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be strictly positive: " + timeout);
        }

        List<ProcessBuilder> builders = new ArrayList<>(stages.size());
        for (PipelineStage stage : stages) {
            ProcessBuilder pb = new ProcessBuilder(stage.command());

            File dir = stage.workingDir() != null ? stage.workingDir() : this.defaultWorkingDir;
            if (dir != null) {
                pb.directory(dir);
            }

            if (!this.defaultEnvironment.isEmpty()) {
                pb.environment().putAll(this.defaultEnvironment);
            }
            if (!stage.environment().isEmpty()) {
                pb.environment().putAll(stage.environment());
            }

            builders.add(pb);
        }

        long startNanos = System.nanoTime();
        List<Process> processes;
        try {
            processes = ProcessBuilder.startPipeline(builders);
        } catch (IOException e) {
            long elapsedNanos = System.nanoTime() - startNanos;
            return new PipelineResult(
                    -1,
                    "",
                    "Pipeline start failed: " + e.getMessage(),
                    Duration.ofNanos(elapsedNanos),
                    false
            );
        }

        // Asynchronously drain stderr for all processes to prevent OS pipe buffer deadlocks
        List<CompletableFuture<String>> stderrFutures = new ArrayList<>(processes.size());
        for (Process p : processes) {
            stderrFutures.add(drainStreamAsync(p.getErrorStream(), maxOutputBytes));
        }

        // Asynchronously drain terminal stdout from the last stage
        Process lastProcess = processes.get(processes.size() - 1);
        CompletableFuture<String> stdoutFuture = drainStreamAsync(lastProcess.getInputStream(), maxOutputBytes);

        boolean timedOut = false;
        long deadlineMillis = System.currentTimeMillis() + timeout.toMillis();

        try {
            for (Process p : processes) {
                long remainingMillis = deadlineMillis - System.currentTimeMillis();
                if (remainingMillis <= 0 || !p.waitFor(remainingMillis, TimeUnit.MILLISECONDS)) {
                    timedOut = true;
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            timedOut = true;
        }

        if (timedOut) {
            for (Process p : processes) {
                killProcessTree(p);
            }
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        Duration executionTime = Duration.ofNanos(elapsedNanos);

        String stdout = awaitStream(stdoutFuture);
        String stderr = combineStderr(stderrFutures);

        if (timedOut) {
            if (!stderr.isEmpty()) {
                stderr += "\n";
            }
            stderr += "Pipeline execution timed out after " + timeout;
            return new PipelineResult(-1, stdout, stderr, executionTime, false);
        }

        // Determine overall pipeline exit code (prefer non-zero exit code if any stage failed)
        int overallExitCode = lastProcess.exitValue();
        if (overallExitCode == 0) {
            for (Process p : processes) {
                if (p.exitValue() != 0) {
                    overallExitCode = p.exitValue();
                    break;
                }
            }
        }

        return new PipelineResult(
                overallExitCode,
                stdout,
                stderr,
                executionTime,
                overallExitCode == 0
        );
    }

    private static CompletableFuture<String> drainStreamAsync(InputStream is, int maxBytes) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Thread.ofVirtual().name("pipeline-stream-drainer").start(() -> {
            try (is; ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int totalRead = 0;
                int read;
                while ((read = is.read(buffer)) != -1) {
                    if (totalRead < maxBytes) {
                        int toWrite = Math.min(read, maxBytes - totalRead);
                        baos.write(buffer, 0, toWrite);
                    }
                    totalRead += read;
                }
                future.complete(baos.toString(StandardCharsets.UTF_8));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static String awaitStream(CompletableFuture<String> future) {
        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String combineStderr(List<CompletableFuture<String>> futures) {
        StringBuilder sb = new StringBuilder();
        for (CompletableFuture<String> f : futures) {
            String err = awaitStream(f).trim();
            if (!err.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(err);
            }
        }
        return sb.toString();
    }

    private static void killProcessTree(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception ignored) {
        }
        process.destroyForcibly();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
