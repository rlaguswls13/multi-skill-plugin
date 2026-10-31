package com.msp.processrunner;

import java.time.Duration;
import java.util.Objects;

/**
 * Execution result of a process execution.
 *
 * @param exitCode process exit code (-1 if timed out or interrupted)
 * @param stdout captured standard output stream
 * @param stderr captured standard error stream
 * @param executionTime total elapsed wall-clock execution duration
 * @param timedOut true if the process exceeded its allotted timeout and was killed
 */
public record ProcessRunResult(
        int exitCode,
        String stdout,
        String stderr,
        Duration executionTime,
        boolean timedOut
) {
    public ProcessRunResult {
        Objects.requireNonNull(stdout, "stdout cannot be null");
        Objects.requireNonNull(stderr, "stderr cannot be null");
        Objects.requireNonNull(executionTime, "executionTime cannot be null");
    }

    /**
     * Checks if the process completed normally without timeout and with an exit code of 0.
     *
     * @return true if exitCode == 0 and not timed out
     */
    public boolean isSuccess() {
        return !timedOut && exitCode == 0;
    }
}
