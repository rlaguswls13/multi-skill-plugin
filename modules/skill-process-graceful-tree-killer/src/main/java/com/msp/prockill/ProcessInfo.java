package com.msp.prockill;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Snapshot of process metadata at a specific point in time.
 *
 * @param pid       Operating system process identifier
 * @param parentPid PID of the direct parent process, if present
 * @param command   Executable path or command name
 * @param arguments Process command-line arguments
 * @param user      OS user account running this process
 * @param isAlive   Whether the process is alive at inspection time
 */
public record ProcessInfo(
    long pid,
    Optional<Long> parentPid,
    String command,
    List<String> arguments,
    Optional<String> user,
    boolean isAlive
) {
    public ProcessInfo {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(arguments, "arguments cannot be null");
        arguments = List.copyOf(arguments);
    }

    public static ProcessInfo of(ProcessHandle handle) {
        Objects.requireNonNull(handle, "handle cannot be null");
        ProcessHandle.Info info = handle.info();
        return new ProcessInfo(
                handle.pid(),
                handle.parent().map(ProcessHandle::pid),
                info.command().orElse(""),
                info.arguments().map(List::of).orElse(List.of()),
                info.user(),
                handle.isAlive()
        );
    }
}
