package com.msp.pidlock;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Result of attempting to acquire an OS-level singleton process PID lockfile.
 *
 * @param acquired       True if the current process successfully acquired the exclusive lock
 * @param holderPid      The PID holding the lock (current PID if acquired; existing holder's PID or -1L if not acquired)
 * @param lockFilePath   Normalized absolute path to the `.pid` lockfile
 * @param staleRecovered True if a pre-existing stale `.pid` file from a dead process was detected and reclaimed
 */
public record PidLockResult(
        boolean acquired,
        long holderPid,
        Path lockFilePath,
        boolean staleRecovered
) {
    public PidLockResult {
        Objects.requireNonNull(lockFilePath, "lockFilePath must not be null");
    }
}
