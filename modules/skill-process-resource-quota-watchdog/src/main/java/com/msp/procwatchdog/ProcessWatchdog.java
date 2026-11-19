package com.msp.procwatchdog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe child process execution watchdog enforcing:
 * <ul>
 *   <li>Maximum wall-clock execution time ({@link TerminationReason#WALL_TIMEOUT})</li>
 *   <li>Maximum stdout/stderr output idle time ({@link TerminationReason#IDLE_TIMEOUT})</li>
 *   <li>Maximum combined stdout/stderr byte quota ({@link TerminationReason#OUTPUT_QUOTA_EXCEEDED})</li>
 * </ul>
 * On any policy violation, terminates the entire process tree (descendants first, graceful then forcible).
 */
public final class ProcessWatchdog {

    private static final int READ_BUFFER_SIZE = 4096;

    public WatchdogResult execute(List<String> command, WatchdogPolicy policy)
            throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (policy.workingDirectory() != null) {
            pb.directory(policy.workingDirectory().toFile());
        }
        if (!policy.environment().isEmpty()) {
            pb.environment().putAll(policy.environment());
        }

        long startNanos = System.nanoTime();
        Process process = pb.start();

        AtomicLong lastOutputNanos = new AtomicLong(startNanos);
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicReference<TerminationReason> violation = new AtomicReference<>(null);

        ByteArrayOutputStream stdoutBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();

        long maxOutputBytes = policy.maxOutputBytes();

        Thread stdoutThread = startStreamDrainer(
                "watchdog-stdout", process, process.getInputStream(), stdoutBuf,
                lastOutputNanos, totalBytes, maxOutputBytes, violation, policy.destroyGracePeriod());
        Thread stderrThread = startStreamDrainer(
                "watchdog-stderr", process, process.getErrorStream(), stderrBuf,
                lastOutputNanos, totalBytes, maxOutputBytes, violation, policy.destroyGracePeriod());

        long maxWallNanos = (policy.maxWallTime() != null) ? policy.maxWallTime().toNanos() : -1L;
        long maxIdleNanos = (policy.maxIdleTime() != null) ? policy.maxIdleTime().toNanos() : -1L;
        long pollMs = Math.max(1L, policy.pollInterval().toMillis());

        try {
            while (!process.waitFor(pollMs, TimeUnit.MILLISECONDS)) {
                if (violation.get() != null) {
                    break;
                }
                long now = System.nanoTime();
                if (maxWallNanos > 0 && (now - startNanos) >= maxWallNanos) {
                    if (violation.compareAndSet(null, TerminationReason.WALL_TIMEOUT)) {
                        terminateProcessTree(process, policy.destroyGracePeriod());
                    }
                    break;
                }
                if (maxIdleNanos > 0 && (now - lastOutputNanos.get()) >= maxIdleNanos) {
                    if (violation.compareAndSet(null, TerminationReason.IDLE_TIMEOUT)) {
                        terminateProcessTree(process, policy.destroyGracePeriod());
                    }
                    break;
                }
            }
        } catch (InterruptedException ie) {
            terminateProcessTree(process, policy.destroyGracePeriod());
            Thread.currentThread().interrupt();
            throw ie;
        } finally {
            if (process.isAlive()) {
                terminateProcessTree(process, policy.destroyGracePeriod());
            }
            joinSilently(stdoutThread, 2_000L);
            joinSilently(stderrThread, 2_000L);
        }

        Duration elapsed = Duration.ofNanos(Math.max(0L, System.nanoTime() - startNanos));
        TerminationReason reason = violation.get();
        if (reason == null) {
            reason = TerminationReason.COMPLETED;
        }
        int exitCode = process.isAlive() ? -1 : process.exitValue();

        return new WatchdogResult(
                exitCode,
                reason,
                stdoutBuf.toByteArray(),
                stderrBuf.toByteArray(),
                totalBytes.get(),
                elapsed
        );
    }

    private static Thread startStreamDrainer(
            String threadName,
            Process process,
            InputStream stream,
            ByteArrayOutputStream target,
            AtomicLong lastOutputNanos,
            AtomicLong totalBytes,
            long maxOutputBytes,
            AtomicReference<TerminationReason> violation,
            Duration gracePeriod
    ) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[READ_BUFFER_SIZE];
            try (InputStream in = stream) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (read == 0) {
                        continue;
                    }
                    lastOutputNanos.set(System.nanoTime());
                    long updatedTotal = totalBytes.addAndGet(read);

                    if (maxOutputBytes > 0) {
                        long prevTotal = updatedTotal - read;
                        long allowed = Math.max(0L, maxOutputBytes - prevTotal);
                        int toWrite = (int) Math.min(read, allowed);
                        if (toWrite > 0) {
                            target.write(buffer, 0, toWrite);
                        }
                        if (updatedTotal > maxOutputBytes) {
                            if (violation.compareAndSet(null, TerminationReason.OUTPUT_QUOTA_EXCEEDED)) {
                                terminateProcessTree(process, gracePeriod);
                            }
                            break;
                        }
                    } else {
                        target.write(buffer, 0, read);
                    }
                }
            } catch (IOException ignored) {
                // Stream closed as a result of process termination
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * Terminates all descendant processes and the root process gracefully, escalating to forcible termination
     * if still alive after {@code gracePeriod}.
     */
    static void terminateProcessTree(Process process, Duration gracePeriod) {
        List<ProcessHandle> descendants = List.of();
        try {
            descendants = process.toHandle().descendants().toList();
        } catch (UnsupportedOperationException ignored) {
            // Platform without ProcessHandle support
        }

        for (ProcessHandle child : descendants) {
            child.destroy();
        }
        process.destroy();

        long graceMs = Math.max(0L, gracePeriod.toMillis());
        try {
            if (graceMs > 0) {
                process.waitFor(graceMs, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (ProcessHandle child : descendants) {
            if (child.isAlive()) {
                child.destroyForcibly();
            }
        }
        if (process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void joinSilently(Thread thread, long timeoutMs) {
        try {
            thread.join(timeoutMs);
            if (thread.isAlive()) {
                thread.interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
