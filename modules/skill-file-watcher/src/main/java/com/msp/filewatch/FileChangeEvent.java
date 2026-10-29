package com.msp.filewatch;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable event describing a file system modification.
 */
public record FileChangeEvent(Path path, FileChangeType type, Instant timestamp) {

    public FileChangeEvent {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public FileChangeEvent(Path path, FileChangeType type) {
        this(path, type, Instant.now());
    }
}
