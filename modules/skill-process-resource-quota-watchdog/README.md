# skill-process-resource-quota-watchdog

Zero-dependency Java 21 child process execution watchdog enforcing **wall-clock timeout**, **output idle timeout**, and **combined stdout/stderr output byte quota** with clean process-tree termination.

## Architecture Highlights
- **Deadlock-Free Dual Stream Draining**: Concurrent daemon reader threads drain `stdout` and `stderr` continuously while tracking last-output timestamp and byte totals.
- **Strict Memory-Bounded Quota (`maxOutputBytes`)**: Stops buffering at the configured quota and immediately terminates the child process tree when exceeded (`OUTPUT_QUOTA_EXCEEDED`).
- **Wall-Clock & Idle Timeouts**: Independently enforces total runtime (`WALL_TIMEOUT`) and silence duration (`IDLE_TIMEOUT`).
- **Process Tree Cleanup**: Uses Java `ProcessHandle.descendants()` to terminate child process trees gracefully first, then forcibly after `destroyGracePeriod`.

## Usage Example
```java
import com.msp.procwatchdog.*;
import java.time.Duration;
import java.util.List;

ProcessWatchdog watchdog = new ProcessWatchdog();
WatchdogPolicy policy = WatchdogPolicy.builder()
        .maxWallTime(Duration.ofSeconds(10))
        .maxIdleTime(Duration.ofSeconds(3))
        .maxOutputBytes(1024 * 1024) // 1 MiB
        .build();

WatchdogResult result = watchdog.execute(List.of("java", "-version"), policy);
System.out.println(result.terminationReason() + " exit=" + result.exitCode());
```
