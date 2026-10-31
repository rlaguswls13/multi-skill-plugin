package com.msp.processpipe;

import java.time.Duration;
import java.util.Objects;

/**
 * Execution result of a multi-stage process pipeline.
 *
 * @param exitCode  the exit code of the pipeline (-1 if timed out or failed to start)
 * @param stdout    the captured standard output of the terminal pipeline stage
 * @param stderr    the consolidated standard error of all pipeline stages
 * @param duration  the total elapsed execution duration
 * @param isSuccess true if the pipeline completed normally with exit code 0
 */
public record PipelineResult(
        int exitCode,
        String stdout,
        String stderr,
        Duration duration,
        boolean isSuccess
) {

    public PipelineResult {
        Objects.requireNonNull(stdout, "stdout must not be null");
        Objects.requireNonNull(stderr, "stderr must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
    }
}
