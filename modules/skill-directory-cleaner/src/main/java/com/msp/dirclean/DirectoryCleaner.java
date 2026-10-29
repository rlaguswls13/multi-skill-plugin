package com.msp.dirclean;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Time-based and pattern-based file and directory cleanup utility with dry-run support.
 * Pure Java 21, zero runtime dependencies.
 */
public final class DirectoryCleaner {

    private DirectoryCleaner() {
    }

    /**
     * Cleans files and directories under root using default options.
     */
    public static CleanReport clean(Path root) throws IOException {
        return clean(root, CleanOptions.DEFAULT);
    }

    /**
     * Cleans files and directories under root according to specified options.
     *
     * @param root    the directory path to clean
     * @param options cleaning configuration options
     * @return CleanReport with counts, bytes freed, and deleted paths
     * @throws IOException if root cannot be read or traversed
     */
    public static CleanReport clean(Path root, CleanOptions options) throws IOException {
        Objects.requireNonNull(root, "root path must not be null");
        Objects.requireNonNull(options, "options must not be null");

        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Root path must be an existing directory: " + root);
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + options.globPattern());
        Instant cutoff = options.olderThan() != null ? Instant.now().minus(options.olderThan()) : null;

        List<Path> deletedFiles = new ArrayList<>();
        List<Path> deletedDirs = new ArrayList<>();
        List<Path> failedPaths = new ArrayList<>();
        Set<Path> plannedDeletedPaths = options.dryRun() ? new HashSet<>() : Collections.emptySet();
        long[] totalFreedBytes = new long[]{0L};

        Set<FileVisitOption> visitOptions = Collections.emptySet();

        Files.walkFileTree(root, visitOptions, options.maxDepth(), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // Check glob pattern against file name
                Path fileName = file.getFileName();
                if (fileName == null || !matcher.matches(fileName)) {
                    return FileVisitResult.CONTINUE;
                }

                // Check olderThan time condition
                if (cutoff != null) {
                    Instant lastModified = attrs.lastModifiedTime().toInstant();
                    if (lastModified.isAfter(cutoff)) {
                        return FileVisitResult.CONTINUE;
                    }
                }

                // Check custom filter if present
                if (options.customFilter() != null && !options.customFilter().test(file)) {
                    return FileVisitResult.CONTINUE;
                }

                // Eligible for deletion
                long size = attrs.size();

                if (options.dryRun()) {
                    totalFreedBytes[0] += size;
                    deletedFiles.add(file);
                    plannedDeletedPaths.add(file);
                } else {
                    try {
                        Files.delete(file);
                        totalFreedBytes[0] += size;
                        deletedFiles.add(file);
                    } catch (IOException e) {
                        failedPaths.add(file);
                    }
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (exc != null) {
                    failedPaths.add(dir);
                    return FileVisitResult.CONTINUE;
                }

                // Do not delete the root directory itself
                if (dir.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }

                if (!options.deleteEmptyDirs()) {
                    return FileVisitResult.CONTINUE;
                }

                try {
                    if (options.dryRun()) {
                        // Check if all children in this dir are marked as planned to be deleted
                        try (Stream<Path> stream = Files.list(dir)) {
                            boolean willBeEmpty = stream.allMatch(plannedDeletedPaths::contains);
                            if (willBeEmpty) {
                                deletedDirs.add(dir);
                                plannedDeletedPaths.add(dir);
                            }
                        }
                    } else {
                        // Check if directory is currently empty on disk
                        try (Stream<Path> stream = Files.list(dir)) {
                            if (stream.findFirst().isEmpty()) {
                                Files.delete(dir);
                                deletedDirs.add(dir);
                            }
                        }
                    }
                } catch (IOException e) {
                    failedPaths.add(dir);
                }

                return FileVisitResult.CONTINUE;
            }
        });

        return new CleanReport(
                deletedFiles.size(),
                deletedDirs.size(),
                totalFreedBytes[0],
                deletedFiles,
                deletedDirs,
                failedPaths
        );
    }
}
