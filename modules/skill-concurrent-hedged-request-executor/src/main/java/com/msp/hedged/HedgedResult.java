package com.msp.hedged;

import java.time.Duration;
import java.util.Objects;

/**
 * Result wrapper providing both the winning response value and metadata about the winning attempt.
 *
 * @param value                  The result returned by the winning attempt
 * @param winningAttemptIndex    0 for primary request, 1..N for speculative hedged attempts
 * @param hedgesFiredForCall     Number of speculative hedged attempts fired during this call
 * @param cancelledAttemptsCount Number of redundant in-flight attempts cancelled when the winner completed
 * @param elapsed                Total wall-clock elapsed time until the winning result was obtained
 * @param <T>                    Result payload type
 */
public record HedgedResult<T>(
        T value,
        int winningAttemptIndex,
        int hedgesFiredForCall,
        int cancelledAttemptsCount,
        Duration elapsed
) {
    public HedgedResult {
        Objects.requireNonNull(elapsed, "elapsed must not be null");
    }

    /**
     * Returns true if the primary request (attempt 0) completed first.
     */
    public boolean isPrimaryWin() {
        return winningAttemptIndex == 0;
    }

    /**
     * Returns true if a secondary speculative hedge request (attempt >= 1) completed first.
     */
    public boolean isHedgedWin() {
        return winningAttemptIndex > 0;
    }
}
