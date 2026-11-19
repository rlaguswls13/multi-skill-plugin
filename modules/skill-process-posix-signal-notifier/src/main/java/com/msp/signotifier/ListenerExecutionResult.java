package com.msp.signotifier;

import java.time.Duration;

/**
 * Result record for a single listener's execution.
 */
public record ListenerExecutionResult(
        String listenerId,
        String listenerName,
        SignalType signalType,
        SignalExecutionStatus status,
        Duration duration,
        Throwable error
) {
    public boolean isSuccess() {
        return status == SignalExecutionStatus.SUCCESS;
    }
}
