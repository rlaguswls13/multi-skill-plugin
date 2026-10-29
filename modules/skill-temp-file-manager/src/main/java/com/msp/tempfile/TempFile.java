package com.msp.tempfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * An AutoCloseable wrapper around a temporary file.
 * Automatically deletes the file when closed or upon JVM termination if registered.
 */
public final class TempFile implements TempResource {

    private final Path path;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean deleteOnClose = true;
    private final Consumer<TempResource> onCloseCallback;

    TempFile(Path path, Consumer<TempResource> onCloseCallback) {
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.onCloseCallback = onCloseCallback;
    }

    public static TempFile of(Path path) {
        return new TempFile(path, null);
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public boolean exists() {
        return Files.exists(path);
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    public boolean isDeleteOnClose() {
        return deleteOnClose;
    }

    public void setDeleteOnClose(boolean deleteOnClose) {
        this.deleteOnClose = deleteOnClose;
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            if (onCloseCallback != null) {
                onCloseCallback.accept(this);
            }
            if (deleteOnClose) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Override
    public String toString() {
        return "TempFile[" + path + ", closed=" + closed.get() + "]";
    }
}
