package com.msp.tempfile;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * An AutoCloseable wrapper around a temporary directory.
 * Automatically and recursively deletes the directory and all contained files/subdirectories upon close.
 */
public final class TempDirectory implements TempResource {

    private final Path path;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean deleteOnClose = true;
    private final Consumer<TempResource> onCloseCallback;

    TempDirectory(Path path, Consumer<TempResource> onCloseCallback) {
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.onCloseCallback = onCloseCallback;
    }

    public static TempDirectory of(Path path) {
        return new TempDirectory(path, null);
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

    /**
     * Resolves a child path inside this temporary directory.
     */
    public Path resolve(String relativePath) {
        return path.resolve(relativePath);
    }

    /**
     * Creates a regular file inside this temporary directory.
     */
    public Path createFile(String fileName) throws IOException {
        Path target = path.resolve(fileName);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.createFile(target);
    }

    /**
     * Creates a subdirectory inside this temporary directory.
     */
    public Path createSubdirectory(String dirName) throws IOException {
        Path target = path.resolve(dirName);
        return Files.createDirectories(target);
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            if (onCloseCallback != null) {
                onCloseCallback.accept(this);
            }
            if (deleteOnClose && Files.exists(path)) {
                deleteRecursively(path);
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                try {
                    Files.deleteIfExists(dir);
                } catch (IOException ignored) {
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public String toString() {
        return "TempDirectory[" + path + ", closed=" + closed.get() + "]";
    }
}
