package com.msp.tar;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a complete entry in a TAR archive, containing both metadata and raw byte content.
 *
 * @param entry   the metadata header information
 * @param content the raw file contents (empty byte array for directories)
 */
public record TarArchiveEntry(TarEntry entry, byte[] content) {

    public TarArchiveEntry {
        Objects.requireNonNull(entry, "entry must not be null");
        content = (content == null) ? new byte[0] : Arrays.copyOf(content, content.length);
    }

    public TarArchiveEntry(String name, long size, long lastModifiedEpochSeconds, boolean isDirectory, byte[] content) {
        this(new TarEntry(name, size, lastModifiedEpochSeconds, isDirectory), content);
    }

    public String name() {
        return entry.name();
    }

    public long size() {
        return entry.size();
    }

    public long lastModifiedEpochSeconds() {
        return entry.lastModifiedEpochSeconds();
    }

    public boolean isDirectory() {
        return entry.isDirectory();
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    public String contentAsString() {
        return new String(content, StandardCharsets.UTF_8);
    }
}
