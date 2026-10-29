package com.msp.dirclean;

import java.nio.file.Path;
import java.util.List;

/**
 * Result report of a directory clean operation.
 *
 * @param deletedFileCount number of files deleted (or marked for deletion if dry-run)
 * @param deletedDirCount  number of empty directories deleted (or marked for deletion if dry-run)
 * @param freedBytes       total bytes freed from deleted files
 * @param deletedFiles     list of deleted file paths
 * @param deletedDirs      list of deleted directory paths
 * @param failedPaths      list of paths that failed to be deleted due to I/O error
 */
public record CleanReport(
        int deletedFileCount,
        int deletedDirCount,
        long freedBytes,
        List<Path> deletedFiles,
        List<Path> deletedDirs,
        List<Path> failedPaths
) {
    public CleanReport {
        deletedFiles = List.copyOf(deletedFiles);
        deletedDirs = List.copyOf(deletedDirs);
        failedPaths = List.copyOf(failedPaths);
    }

    /**
     * Returns true if any file or directory deletion failed.
     */
    public boolean hasFailures() {
        return !failedPaths.isEmpty();
    }
}
