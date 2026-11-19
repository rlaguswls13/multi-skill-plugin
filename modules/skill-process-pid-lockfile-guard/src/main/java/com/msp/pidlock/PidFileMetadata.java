package com.msp.pidlock;

import java.time.Instant;
import java.util.Objects;

/**
 * Structured metadata stored inside a `.pid` lockfile.
 *
 * @param pid       Process ID of the lock owner
 * @param startedAt ISO-8601 timestamp when the lock or process started
 * @param command   Command line or executable description of the owning process
 */
public record PidFileMetadata(
        long pid,
        Instant startedAt,
        String command
) {
    public PidFileMetadata {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(command, "command must not be null");
    }

    public String serialize() {
        return "pid=" + pid + "\n"
                + "startedAt=" + startedAt + "\n"
                + "command=" + command.replace('\n', ' ').replace('\r', ' ') + "\n";
    }
}
