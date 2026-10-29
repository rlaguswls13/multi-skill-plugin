package com.msp.filewatch;

/**
 * Types of filesystem changes observed by {@link FileWatcher}.
 */
public enum FileChangeType {
    /**
     * A new file or directory was created.
     */
    CREATED,

    /**
     * An existing file or directory was modified.
     */
    MODIFIED,

    /**
     * A file or directory was deleted.
     */
    DELETED
}
