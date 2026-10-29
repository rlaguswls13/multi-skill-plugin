package com.msp.filewatch;

import java.nio.file.Path;

/**
 * Functional callback interface invoked when a debounced filesystem change occurs.
 */
@FunctionalInterface
public interface FileChangeListener {

    /**
     * Invoked when a file is created, modified, or deleted.
     *
     * @param path the absolute path of the target file or directory
     * @param type the kind of change
     */
    void onChange(Path path, FileChangeType type);

    /**
     * Convenience method delegating to {@link #onChange(Path, FileChangeType)}.
     */
    default void onEvent(FileChangeEvent event) {
        if (event != null) {
            onChange(event.path(), event.type());
        }
    }
}
