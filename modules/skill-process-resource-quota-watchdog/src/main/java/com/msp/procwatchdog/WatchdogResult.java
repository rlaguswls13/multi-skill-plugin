package com.msp.procwatchdog;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

/**
 * Structured outcome of a watchdog-supervised child process execution.
 */
public record WatchdogResult(
        int exitCode,
        TerminationReason terminationReason,
        byte[] stdout,
        byte[] stderr,
        long totalOutputBytes,
        Duration elapsed
) {
    public WatchdogResult {
        Objects.requireNonNull(terminationReason, "terminationReason must not be null");
        Objects.requireNonNull(stdout, "stdout must not be null");
        Objects.requireNonNull(stderr, "stderr must not be null");
        Objects.requireNonNull(elapsed, "elapsed must not be null");
        stdout = Arrays.copyOf(stdout, stdout.length);
        stderr = Arrays.copyOf(stderr, stderr.length);
    }

    @Override
    public byte[] stdout() {
        return Arrays.copyOf(stdout, stdout.length);
    }

    @Override
    public byte[] stderr() {
        return Arrays.copyOf(stderr, stderr.length);
    }

    public String stdoutUtf8() {
        return new String(stdout, StandardCharsets.UTF_8);
    }

    public String stderrUtf8() {
        return new String(stderr, StandardCharsets.UTF_8);
    }

    public boolean isCompleted() {
        return terminationReason == TerminationReason.COMPLETED;
    }
}
