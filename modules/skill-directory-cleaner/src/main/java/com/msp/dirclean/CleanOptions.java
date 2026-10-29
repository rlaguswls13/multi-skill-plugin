package com.msp.dirclean;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Configuration options for DirectoryCleaner.
 *
 * @param olderThan        delete files older than this duration (relative to current time); null deletes regardless of age
 * @param globPattern      glob pattern for matching file names (e.g. "*.log", "*.tmp", default "*")
 * @param deleteEmptyDirs  whether to delete directories that are empty or become empty after cleanup
 * @param dryRun           if true, calculates what would be deleted without actually deleting
 * @param maxDepth         maximum directory traversal depth (default Integer.MAX_VALUE)
 * @param customFilter     optional custom filter predicate
 */
public record CleanOptions(
        Duration olderThan,
        String globPattern,
        boolean deleteEmptyDirs,
        boolean dryRun,
        int maxDepth,
        Predicate<Path> customFilter
) {
    public static final CleanOptions DEFAULT = builder().build();

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Duration olderThan;
        private String globPattern = "*";
        private boolean deleteEmptyDirs = false;
        private boolean dryRun = false;
        private int maxDepth = Integer.MAX_VALUE;
        private Predicate<Path> customFilter;

        private Builder() {
        }

        public Builder olderThan(Duration olderThan) {
            this.olderThan = olderThan;
            return this;
        }

        public Builder globPattern(String globPattern) {
            this.globPattern = Objects.requireNonNullElse(globPattern, "*");
            return this;
        }

        public Builder deleteEmptyDirs(boolean deleteEmptyDirs) {
            this.deleteEmptyDirs = deleteEmptyDirs;
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder maxDepth(int maxDepth) {
            if (maxDepth < 0) {
                throw new IllegalArgumentException("maxDepth must be non-negative");
            }
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder filter(Predicate<Path> customFilter) {
            this.customFilter = customFilter;
            return this;
        }

        public CleanOptions build() {
            return new CleanOptions(
                    olderThan,
                    globPattern,
                    deleteEmptyDirs,
                    dryRun,
                    maxDepth,
                    customFilter
            );
        }
    }
}
