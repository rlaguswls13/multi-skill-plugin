package com.msp.tar;

import java.time.Instant;
import java.util.Objects;

/**
 * Metadata record for a single entry in a TAR archive.
 *
 * @param name                     file or directory path in archive
 * @param size                     byte length of file content (0 for directories)
 * @param lastModifiedEpochSeconds last modification timestamp in epoch seconds
 * @param isDirectory              true if this entry represents a directory
 */
public record TarEntry(String name, long size, long lastModifiedEpochSeconds, boolean isDirectory) {

    public TarEntry {
        Objects.requireNonNull(name, "name must not be null");
        if (size < 0) {
            throw new IllegalArgumentException("size cannot be negative: " + size);
        }
        if (isDirectory && !name.endsWith("/")) {
            name = name + "/";
        }
    }

    public static TarEntry file(String name, long size, long lastModifiedEpochSeconds) {
        return new TarEntry(name, size, lastModifiedEpochSeconds, false);
    }

    public static TarEntry file(String name, long size) {
        return new TarEntry(name, size, Instant.now().getEpochSecond(), false);
    }

    public static TarEntry directory(String name, long lastModifiedEpochSeconds) {
        return new TarEntry(name, 0L, lastModifiedEpochSeconds, true);
    }

    public static TarEntry directory(String name) {
        return new TarEntry(name, 0L, Instant.now().getEpochSecond(), true);
    }
}
