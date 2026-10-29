package com.msp.filewatch;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Thread-safe file and directory watcher utilizing Java NIO {@link WatchService}.
 * Features event debouncing to consolidate burst OS filesystem events into single callbacks,
 * and optional recursive subdirectory monitoring.
 */
public final class FileWatcher implements AutoCloseable {

    private final Path rootDirectory;
    private final boolean recursive;
    private final Duration debounceQuietPeriod;
    private final FileChangeListener listener;

    private WatchService watchService;
    private final Map<WatchKey, Path> watchKeyMap = new ConcurrentHashMap<>();
    private final Map<Path, DebounceTask> pendingEvents = new ConcurrentHashMap<>();

    private ScheduledExecutorService debounceScheduler;
    private Thread watcherThread;
    private volatile boolean running = false;

    private record DebounceTask(FileChangeType type, ScheduledFuture<?> future) {}

    private FileWatcher(Builder builder) {
        this.rootDirectory = Objects.requireNonNull(builder.directory, "directory must not be null").toAbsolutePath().normalize();
        this.recursive = builder.recursive;
        this.debounceQuietPeriod = builder.debounceQuietPeriod != null ? builder.debounceQuietPeriod : Duration.ofMillis(300);
        this.listener = Objects.requireNonNull(builder.listener, "listener must not be null");

        if (!Files.isDirectory(rootDirectory)) {
            throw new IllegalArgumentException("Path must be an existing directory: " + rootDirectory);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts monitoring the directory.
     */
    public synchronized FileWatcher start() {
        if (running) {
            return this;
        }

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.debounceScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "file-watcher-debounce");
                t.setDaemon(true);
                return t;
            });

            if (recursive) {
                registerAll(rootDirectory);
            } else {
                registerSingle(rootDirectory);
            }

            this.running = true;
            this.watcherThread = new Thread(this::runLoop, "file-watcher-loop-" + rootDirectory.getFileName());
            this.watcherThread.setDaemon(true);
            this.watcherThread.start();

            return this;
        } catch (IOException e) {
            throw new FileWatcherException("Failed to initialize file watcher for: " + rootDirectory, e);
        }
    }

    private void runLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }

            Path parentDir = watchKeyMap.get(key);
            if (parentDir == null) {
                key.cancel();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path relative = pathEvent.context();
                Path targetPath = parentDir.resolve(relative).toAbsolutePath().normalize();

                FileChangeType changeType;
                if (kind == ENTRY_CREATE) {
                    changeType = FileChangeType.CREATED;
                    if (recursive && Files.isDirectory(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                        try {
                            registerAll(targetPath);
                        } catch (IOException ignored) {
                        }
                    }
                } else if (kind == ENTRY_MODIFY) {
                    changeType = FileChangeType.MODIFIED;
                } else if (kind == ENTRY_DELETE) {
                    changeType = FileChangeType.DELETED;
                } else {
                    continue;
                }

                enqueueDebounced(targetPath, changeType);
            }

            boolean valid = key.reset();
            if (!valid) {
                watchKeyMap.remove(key);
                if (watchKeyMap.isEmpty()) {
                    break;
                }
            }
        }
    }

    private void enqueueDebounced(Path path, FileChangeType newType) {
        if (!running || debounceScheduler.isShutdown()) {
            return;
        }

        pendingEvents.compute(path, (p, existing) -> {
            FileChangeType mergedType;
            if (existing != null) {
                existing.future().cancel(false);
                // Coalesce rapid events
                if (existing.type() == FileChangeType.CREATED && newType == FileChangeType.MODIFIED) {
                    mergedType = FileChangeType.CREATED;
                } else if (existing.type() == FileChangeType.CREATED && newType == FileChangeType.DELETED) {
                    return null; // File appeared and disappeared before event dispatched
                } else {
                    mergedType = newType;
                }
            } else {
                mergedType = newType;
            }

            try {
                ScheduledFuture<?> future = debounceScheduler.schedule(() -> {
                    pendingEvents.remove(p);
                    try {
                        listener.onChange(p, mergedType);
                    } catch (Throwable ignored) {
                    }
                }, debounceQuietPeriod.toMillis(), TimeUnit.MILLISECONDS);

                return new DebounceTask(mergedType, future);
            } catch (RejectedExecutionException e) {
                return null;
            }
        });
    }

    private void registerSingle(Path dir) throws IOException {
        WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        watchKeyMap.put(key, dir);
    }

    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerSingle(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Stops the watcher and releases all filesystem resources.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        // Cancel all pending debounce tasks
        for (DebounceTask task : pendingEvents.values()) {
            task.future().cancel(false);
        }
        pendingEvents.clear();

        if (debounceScheduler != null) {
            debounceScheduler.shutdownNow();
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }

        if (watcherThread != null) {
            watcherThread.interrupt();
        }

        watchKeyMap.clear();
    }

    /**
     * Checks if the watcher is currently active.
     */
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        stop();
    }

    public static final class Builder {
        private Path directory;
        private boolean recursive = false;
        private Duration debounceQuietPeriod = Duration.ofMillis(300);
        private FileChangeListener listener;

        public Builder directory(Path directory) {
            this.directory = directory;
            return this;
        }

        public Builder recursive(boolean recursive) {
            this.recursive = recursive;
            return this;
        }

        public Builder debounce(Duration debounceQuietPeriod) {
            this.debounceQuietPeriod = debounceQuietPeriod;
            return this;
        }

        public Builder listener(FileChangeListener listener) {
            this.listener = listener;
            return this;
        }

        public FileWatcher build() {
            return new FileWatcher(this);
        }

        public FileWatcher start() {
            return build().start();
        }
    }
}
