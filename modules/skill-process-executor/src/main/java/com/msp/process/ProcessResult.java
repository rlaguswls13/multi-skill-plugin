package com.msp.process;

import java.time.Duration;
import java.util.Objects;

/**
 * Encapsulates the execution result of an OS process.
 *
 * @param exitCode   the termination exit code of the process (-1 if timed out or interrupted)
 * @param stdout     captured standard output as string
 * @param stderr     captured standard error as string
 * @param duration   wall-clock elapsed duration of the process execution
 * @param isTimedOut true if the process was terminated due to timeout expiration
 */
public record ProcessResult(
        int exitCode,
        String stdout,
        String stderr,
        Duration duration,
        boolean isTimedOut
) {
    public ProcessResult {
        Objects.requireNonNull(stdout, "stdout must not be null");
        Objects.requireNonNull(stderr, "stderr must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
    }

    /**
     * Checks if the process completed successfully with exit code 0 and did not time out.
     *
     * @return true if exitCode == 0 and not timed out
     */
    public boolean isSuccess() {
        return !isTimedOut && exitCode == 0;
    }

    /**
     * Asserts that the process succeeded, throwing an {@link IllegalStateException} if it failed or timed out.
     *
     * @return this result
     * @throws IllegalStateException if process failed
     */
    public ProcessResult assertSuccess() {
        if (!isSuccess()) {
            throw new IllegalStateException(String.format(
                    "Process execution failed (exitCode=%d, timedOut=%b): %s",
                    exitCode, isTimedOut, stderr.isBlank() ? stdout : stderr
            ));
        }
        return this;
    }
}
