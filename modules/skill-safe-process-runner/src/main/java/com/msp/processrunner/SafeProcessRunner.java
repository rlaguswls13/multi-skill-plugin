package com.msp.processrunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Pure Java 21 safe process executor that prevents OS pipe buffer deadlock,
 * enforces memory upper-bounds to prevent OutOfMemoryError, and guarantees process tree cleanup on timeout.
 */
public final class SafeProcessRunner {

    public static final int DEFAULT_MAX_OUTPUT_BYTES = 5 * 1024 * 1024; // 5 MB

    private SafeProcessRunner() {
        // Utility class
    }

    /**
     * Executes an external process command with a guaranteed timeout.
     *
     * @param command command line and arguments
     * @param timeout maximum execution duration
     * @return execution result
     */
    public static ProcessRunResult run(List<String> command, Duration timeout) {
        return run(command, null, null, timeout, DEFAULT_MAX_OUTPUT_BYTES);
    }

    /**
     * Executes an external process command with working directory and environment variables.
     *
     * @param command command line and arguments
     * @param workingDir working directory, or null for current directory
     * @param env environment variable overrides, or null
     * @param timeout maximum execution duration
     * @return execution result
     */
    public static ProcessRunResult run(List<String> command, File workingDir, Map<String, String> env, Duration timeout) {
        return run(command, workingDir, env, timeout, DEFAULT_MAX_OUTPUT_BYTES);
    }

    /**
     * Executes an external process command with full parameter control.
     *
     * @param command command line and arguments
     * @param workingDir working directory, or null for current directory
     * @param env environment variable overrides, or null
     * @param timeout maximum execution duration
     * @param maxOutputBytes maximum bytes captured for stdout and stderr (additional bytes drained and discarded)
     * @return execution result
     */
    public static ProcessRunResult run(List<String> command, File workingDir, Map<String, String> env, Duration timeout, int maxOutputBytes) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be strictly positive: " + timeout);
        }
        if (maxOutputBytes <= 0) {
            throw new IllegalArgumentException("maxOutputBytes must be strictly positive: " + maxOutputBytes);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir);
        }
        if (env != null && !env.isEmpty()) {
            pb.environment().putAll(env);
        }

        long startNanos = System.nanoTime();
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            long elapsedNanos = System.nanoTime() - startNanos;
            return new ProcessRunResult(
                    -1,
                    "",
                    "Process start failed: " + e.getMessage(),
                    Duration.ofNanos(elapsedNanos),
                    false
            );
        }

        // Asynchronously drain stdout and stderr using Java 21 Virtual Threads
        CompletableFuture<String> stdoutFuture = drainStreamAsync(process.getInputStream(), maxOutputBytes);
        CompletableFuture<String> stderrFuture = drainStreamAsync(process.getErrorStream(), maxOutputBytes);

        boolean timedOut = false;
        int exitCode;

        try {
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                timedOut = true;
                exitCode = -1;
                killProcessTree(process);
            } else {
                exitCode = process.exitValue();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            timedOut = true;
            exitCode = -1;
            killProcessTree(process);
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        Duration executionTime = Duration.ofNanos(elapsedNanos);

        String stdout = awaitStreamResult(stdoutFuture);
        String stderr = awaitStreamResult(stderrFuture);

        return new ProcessRunResult(exitCode, stdout, stderr, executionTime, timedOut);
    }

    private static CompletableFuture<String> drainStreamAsync(InputStream is, int maxBytes) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Thread.ofVirtual().name("safe-stream-drainer").start(() -> {
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

    private static String awaitStreamResult(CompletableFuture<String> future) {
        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private static void killProcessTree(Process process) {
        try {
            // Destroy all child descendants first (Java 9+ process tree API)
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
