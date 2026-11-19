# skill-process-pid-lockfile-guard

OS-Level Singleton Process PID Lockfile Guard using NIO `FileChannel` Lock & `ProcessHandle` Liveness Detection in pure Java 21.

## Features
- **Exclusive OS FileChannel Lock**: Acquires a non-blocking exclusive lock (`FileChannel.tryLock()`) on a `.pid` lockfile and writes the current PID (`ProcessHandle.current().pid()`), start timestamp, and command line info.
- **Automatic Stale Lock Recovery**: If a `.pid` file already exists without an active OS lock and the recorded PID is no longer alive (`ProcessHandle.of(pid)` is empty or not alive), safely reclaims the stale lockfile and sets `staleRecovered = true` in `PidLockResult`.
- **Clean AutoCloseable & Shutdown Hook Cleanup**: Releases the `FileLock`, closes the channel, and deletes the `.pid` file on `close()` or JVM exit.

## Usage Example

```java
package com.msp.pidlock.example;

import com.msp.pidlock.PidLockResult;
import com.msp.pidlock.PidLockfileGuard;

import java.nio.file.Path;

public class PidLockExample {
    public static void main(String[] args) throws Exception {
        Path pidFile = Path.of("./build/my-daemon.pid");

        try (PidLockfileGuard guard = PidLockfileGuard.tryAcquire(pidFile)) {
            PidLockResult result = guard.result();
            if (!result.acquired()) {
                System.err.println("Another instance is already running with PID: " + result.holderPid());
                return;
            }
            System.out.println("Lock acquired! Current PID: " + result.holderPid()
                    + " (staleRecovered=" + result.staleRecovered() + ")");
            // Run singleton daemon logic...
        }
    }
}
```
