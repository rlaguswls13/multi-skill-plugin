package com.msp.procwatchdog;

/**
 * Reason why a supervised child process execution ended.
 */
public enum TerminationReason {
    /** Process exited naturally within all configured quotas and timeouts. */
    COMPLETED,
    /** Process exceeded the maximum allowed wall-clock execution duration. */
    WALL_TIMEOUT,
    /** Process produced no stdout/stderr output for longer than the configured idle timeout. */
    IDLE_TIMEOUT,
    /** Process exceeded the combined stdout/stderr output byte quota. */
    OUTPUT_QUOTA_EXCEEDED
}
