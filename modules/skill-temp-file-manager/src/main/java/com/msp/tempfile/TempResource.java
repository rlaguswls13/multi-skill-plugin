package com.msp.tempfile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Common AutoCloseable interface for managed temporary files and directories.
 */
public sealed interface TempResource extends AutoCloseable permits TempFile, TempDirectory {

    /**
     * The underlying filesystem path of the temporary resource.
     */
    Path path();

    /**
     * Checks whether the underlying temporary resource currently exists on the filesystem.
     */
    boolean exists();

    /**
     * Checks whether this resource has already been closed.
     */
    boolean isClosed();

    /**
     * Closes and automatically deletes the resource and any nested content.
     */
    @Override
    void close() throws IOException;
}
