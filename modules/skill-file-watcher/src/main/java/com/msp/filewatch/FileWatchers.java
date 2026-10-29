package com.msp.filewatch;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Convenient static factory and facade for starting file watchers.
 */
public final class FileWatchers {

    private FileWatchers() {
    }

    /**
     * Creates a builder for custom file watcher configuration.
     */
    public static FileWatcher.Builder builder() {
        return FileWatcher.builder();
    }

    /**
     * Watches a directory (non-recursively) with default 300ms debounce and immediately starts it.
     */
    public static FileWatcher watch(Path directory, FileChangeListener listener) {
        return builder().directory(directory).listener(listener).start();
    }

    /**
     * Watches a directory (non-recursively) with custom debounce and immediately starts it.
     */
    public static FileWatcher watch(Path directory, Duration debounce, FileChangeListener listener) {
        return builder().directory(directory).debounce(debounce).listener(listener).start();
    }

    /**
     * Watches a directory recursively with default 300ms debounce and immediately starts it.
     */
    public static FileWatcher watchRecursive(Path directory, FileChangeListener listener) {
        return builder().directory(directory).recursive(true).listener(listener).start();
    }

    /**
     * Watches a directory recursively with custom debounce and immediately starts it.
     */
    public static FileWatcher watchRecursive(Path directory, Duration debounce, FileChangeListener listener) {
        return builder().directory(directory).recursive(true).debounce(debounce).listener(listener).start();
    }
}
