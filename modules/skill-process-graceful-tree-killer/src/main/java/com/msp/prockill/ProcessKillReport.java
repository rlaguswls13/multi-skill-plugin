package com.msp.prockill;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Detailed outcome report of a process tree termination operation.
 *
 * @param targetPid            Root PID targeted for termination
 * @param terminatedGracefully Processes that exited gracefully during stage 1
 * @param killedForcibly       Processes killed forcibly during stage 2 (destroyForcibly)
 * @param failed               Processes that could not be terminated
 * @param totalDuration        Time elapsed across all stages
 * @param dryRun               Whether this execution was a dry run (no signals sent)
 */
public record ProcessKillReport(
    long targetPid,
    List<ProcessInfo> terminatedGracefully,
    List<ProcessInfo> killedForcibly,
    List<ProcessInfo> failed,
    Duration totalDuration,
    boolean dryRun
) {
    public ProcessKillReport {
        terminatedGracefully = List.copyOf(Objects.requireNonNull(terminatedGracefully, "terminatedGracefully cannot be null"));
        killedForcibly = List.copyOf(Objects.requireNonNull(killedForcibly, "killedForcibly cannot be null"));
        failed = List.copyOf(Objects.requireNonNull(failed, "failed cannot be null"));
        Objects.requireNonNull(totalDuration, "totalDuration cannot be null");
    }

    public boolean isSuccessful() {
        return failed.isEmpty();
    }

    public int totalAffected() {
        return terminatedGracefully.size() + killedForcibly.size() + failed.size();
    }
}
