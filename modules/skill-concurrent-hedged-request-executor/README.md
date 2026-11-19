# skill-concurrent-hedged-request-executor

Tail-Latency (P99) Reduction Hedged Request Executor in pure Java 21 (`java.util.concurrent.*`).

## Features
- **Immediate Primary + Delayed Speculative Hedges**: Fires the primary request immediately and launches up to `maxHedgedAttempts` secondary speculative requests if the primary has not completed within `hedgeDelay` (e.g. 50ms).
- **First-Completed Winner & Automatic Cancellation**: Adopts the first successful completion (`firstCompleted`) and automatically cancels remaining in-flight redundant requests and pending timer tasks.
- **Configurable Exception Predicate**:
  - If an attempt throws an exception matching `hedgeOnException(Predicate<Throwable>)`, it immediately triggers the next available speculative hedge without waiting for `hedgeDelay`.
  - If the exception is non-retriable (predicate returns `false`), the execution aborts immediately and cancels any other in-flight attempts.
- **Built-in Telemetry Metrics**: Tracks `primaryWins`, `hedgedWins`, `totalHedgesFired`, and `cancelledRedundantCalls`.

## Usage Example

```java
package com.msp.hedged.example;

import com.msp.hedged.HedgedMetrics;
import com.msp.hedged.HedgedRequestExecutor;
import com.msp.hedged.HedgedResult;

import java.time.Duration;

public class HedgedExample {
    public static void main(String[] args) throws Exception {
        try (HedgedRequestExecutor executor = HedgedRequestExecutor.builder()
                .hedgeDelay(Duration.ofMillis(50))
                .maxHedgedAttempts(2)
                .hedgeOnException(ex -> !(ex instanceof IllegalArgumentException))
                .build()) {

            HedgedResult<String> result = executor.executeWithMetadata(attemptIndex -> {
                if (attemptIndex == 0) {
                    // Simulate P99 GC pause on primary replica
                    Thread.sleep(300);
                    return "response-from-primary";
                }
                // Secondary replica responds quickly
                Thread.sleep(10);
                return "response-from-hedge-" + attemptIndex;
            });

            System.out.println("Winner Value: " + result.value());
            System.out.println("Winning Attempt Index: " + result.winningAttemptIndex());
            System.out.println("Cancelled Redundant Attempts: " + result.cancelledAttemptsCount());

            HedgedMetrics metrics = executor.metrics();
            System.out.println("Metrics: " + metrics);
        }
    }
}
```
