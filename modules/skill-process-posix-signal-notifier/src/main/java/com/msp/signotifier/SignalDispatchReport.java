package com.msp.signotifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Detailed report containing execution metrics and results of all dispatched listeners for a signal.
 */
public record SignalDispatchReport(
        SignalEvent event,
        Instant startTime,
        Instant endTime,
        Duration totalDuration,
        List<ListenerExecutionResult> results
) {
    public SignalDispatchReport {
        results = results != null ? Collections.unmodifiableList(results) : Collections.emptyList();
    }

    public boolean isAllSuccess() {
        return results.stream().allMatch(ListenerExecutionResult::isSuccess);
    }

    public int getSuccessCount() {
        return (int) results.stream().filter(r -> r.status() == SignalExecutionStatus.SUCCESS).count();
    }

    public int getFailedCount() {
        return (int) results.stream().filter(r -> r.status() == SignalExecutionStatus.FAILED).count();
    }

    public int getTimeoutCount() {
        return (int) results.stream().filter(r -> r.status() == SignalExecutionStatus.TIMEOUT).count();
    }

    public int getTotalListeners() {
        return results.size();
    }
}
