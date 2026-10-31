package com.msp.targz;

import java.time.Instant;
import java.util.Objects;

/**
 * Metadata record for an entry within a TAR archive.
 *
 * @param name        the path/name of the entry (e.g. "dir/file.txt" or "dir/")
 * @param size        uncompressed size in bytes (0 for directories)
 * @param isDirectory true if the entry is a directory
 * @param mtime       last modification time
 */
public record TarEntryInfo(
        String name,
        long size,
        boolean isDirectory,
        Instant mtime
) {
    public TarEntryInfo {
        Objects.requireNonNull(name, "Entry name must not be null");
        Objects.requireNonNull(mtime, "mtime must not be null");
        if (size < 0) {
            throw new IllegalArgumentException("Size cannot be negative: " + size);
        }
    }
}
