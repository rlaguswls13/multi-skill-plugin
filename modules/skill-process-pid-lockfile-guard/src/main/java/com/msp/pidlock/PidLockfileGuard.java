package com.msp.pidlock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OS-Level Singleton Process PID Lockfile Guard using {@link FileChannel} Lock & {@link ProcessHandle} Liveness Detection.
 * <p>
 * Features:
 * <ul>
 *     <li>Acquires an exclusive lock on a {@code .pid} lockfile using NIO {@link FileChannel#tryLock()} and writes
 *         the current PID ({@code ProcessHandle.current().pid()}), start timestamp, and command info.</li>
 *     <li>Stale lock recovery: if a {@code .pid} file already exists with content before locking and the recorded PID
 *         is no longer alive ({@code ProcessHandle.of(pid)} is empty or not alive), safely reclaims the stale lockfile
 *         and flags {@code staleRecovered = true} in {@link PidLockResult}.</li>
 *     <li>Protects against both cross-process contention (via OS {@link FileLock}) and intra-JVM contention
 *         (via {@link OverlappingFileLockException} and active path registry).</li>
 *     <li>Implements {@link AutoCloseable} to release the {@link FileLock} and delete the {@code .pid} file cleanly
 *         on {@link #close()} or optional JVM shutdown hook.</li>
 * </ul>
 */
public final class PidLockfileGuard implements AutoCloseable {

    private static final Map<Path, FileChannel> ACTIVE_JVM_LOCK_CHANNELS = new ConcurrentHashMap<>();

    private final Path lockFilePath;
    private final PidLockResult result;
    private final FileChannel channel;
    private final FileLock fileLock;
    private final Thread shutdownHook;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private PidLockfileGuard(
            Path lockFilePath,
            PidLockResult result,
            FileChannel channel,
            FileLock fileLock,
            boolean registerShutdownHook
    ) {
        this.lockFilePath = lockFilePath;
        this.result = result;
        this.channel = channel;
        this.fileLock = fileLock;
        if (result.acquired() && registerShutdownHook) {
            this.shutdownHook = Thread.ofPlatform().unstarted(this::releaseResourcesQuietly);
            try {
                Runtime.getRuntime().addShutdownHook(this.shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down
            }
        } else {
            this.shutdownHook = null;
        }
    }

    /**
     * Attempts to acquire an exclusive OS-level PID lock on {@code lockFile} with a JVM shutdown hook enabled.
     *
     * @param lockFile Path to the `.pid` lockfile
     * @return {@link PidLockfileGuard} whose {@link #result()} details acquisition status
     * @throws IOException if an unexpected I/O error occurs opening the lockfile directory
     */
    public static PidLockfileGuard tryAcquire(Path lockFile) throws IOException {
        return tryAcquire(lockFile, true);
    }

    /**
     * Attempts to acquire an exclusive OS-level PID lock on {@code lockFile}.
     *
     * @param lockFile             Path to the `.pid` lockfile
     * @param registerShutdownHook Whether to register a JVM shutdown hook to release and delete the lockfile on exit
     * @return {@link PidLockfileGuard} whose {@link #result()} details acquisition status
     * @throws IOException if an unexpected I/O error occurs
     */
    public static PidLockfileGuard tryAcquire(Path lockFile, boolean registerShutdownHook) throws IOException {
        Objects.requireNonNull(lockFile, "lockFile must not be null");
        Path normalizedPath = lockFile.toAbsolutePath().normalize();
        Path parent = normalizedPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        long currentPid = ProcessHandle.current().pid();

        // 1. Check intra-JVM lock registry first to avoid closing or corrupting an in-process channel
        if (ACTIVE_JVM_LOCK_CHANNELS.containsKey(normalizedPath)) {
            return new PidLockfileGuard(
                    normalizedPath,
                    new PidLockResult(false, currentPid, normalizedPath, false),
                    null,
                    null,
                    false
            );
        }

        boolean keepInRegistry = false;
        FileChannel fc = null;
        FileLock lock = null;
        try {
            // Inspect pre-existing lockfile content before acquiring lock to detect stale PIDs
            Optional<PidFileMetadata> preExistingMeta = readMetadataLossless(normalizedPath);

            fc = FileChannel.open(
                    normalizedPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
            );

            if (ACTIVE_JVM_LOCK_CHANNELS.putIfAbsent(normalizedPath, fc) != null) {
                closeChannelQuietly(fc);
                return new PidLockfileGuard(
                        normalizedPath,
                        new PidLockResult(false, currentPid, normalizedPath, false),
                        null,
                        null,
                        false
                );
            }

            try {
                lock = fc.tryLock();
            } catch (OverlappingFileLockException e) {
                lock = null;
            } catch (IOException lockEx) {
                // On Windows, locking a file already locked by another handle can throw IOException
                lock = null;
            }

            if (lock == null) {
                // Failed to acquire OS lock; another process currently holds the lock
                final FileChannel openChannel = fc;
                long holderPid = preExistingMeta.map(PidFileMetadata::pid)
                        .or(() -> readMetadataFromChannel(openChannel).map(PidFileMetadata::pid))
                        .orElse(-1L);
                closeChannelQuietly(fc);
                return new PidLockfileGuard(
                        normalizedPath,
                        new PidLockResult(false, holderPid, normalizedPath, false),
                        null,
                        null,
                        false
                );
            }

            // We acquired the OS FileLock! Check if there was a pre-existing PID recorded in the file.
            boolean staleRecovered = false;
            if (preExistingMeta.isPresent()) {
                long oldPid = preExistingMeta.get().pid();
                if (oldPid > 0 && oldPid != currentPid) {
                    if (isProcessAlive(oldPid)) {
                        // A live process is recorded in the file despite no OS lock (e.g. non-locking writer).
                        // Treat as held by that live process and release our lock without overwriting.
                        releaseLockQuietly(lock);
                        closeChannelQuietly(fc);
                        return new PidLockfileGuard(
                                normalizedPath,
                                new PidLockResult(false, oldPid, normalizedPath, false),
                                null,
                                null,
                                false
                        );
                    } else {
                        // Recorded PID is dead -> stale lock recovery!
                        staleRecovered = true;
                    }
                } else if (oldPid <= 0) {
                    staleRecovered = true;
                }
            }

            // Write current process PID, start timestamp, and command info into the locked channel
            PidFileMetadata currentMeta = buildCurrentProcessMetadata(currentPid);
            byte[] payload = currentMeta.serialize().getBytes(StandardCharsets.UTF_8);
            fc.truncate(0);
            fc.position(0);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            while (buffer.hasRemaining()) {
                fc.write(buffer);
            }
            fc.force(true);

            keepInRegistry = true;
            PidLockResult successResult = new PidLockResult(true, currentPid, normalizedPath, staleRecovered);
            return new PidLockfileGuard(normalizedPath, successResult, fc, lock, registerShutdownHook);
        } finally {
            if (!keepInRegistry) {
                ACTIVE_JVM_LOCK_CHANNELS.remove(normalizedPath);
            }
        }
    }

    /**
     * Checks whether the given OS PID is currently alive according to {@link ProcessHandle#of(long)}.
     */
    public static boolean isProcessAlive(long pid) {
        if (pid <= 0) {
            return false;
        }
        try {
            return ProcessHandle.of(pid)
                    .map(ProcessHandle::isAlive)
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reads and parses the {@link PidFileMetadata} from an existing `.pid` file if readable.
     * Supports reading from the active in-JVM locked {@link FileChannel} on Windows where byte-range file locks
     * prevent second file handles from reading the locked bytes.
     */
    public static Optional<PidFileMetadata> readMetadataLossless(Path lockFile) {
        if (lockFile == null) {
            return Optional.empty();
        }
        Path normalized = lockFile.toAbsolutePath().normalize();
        FileChannel activeChannel = ACTIVE_JVM_LOCK_CHANNELS.get(normalized);
        if (activeChannel != null && activeChannel.isOpen()) {
            Optional<PidFileMetadata> fromChannel = readMetadataFromChannel(activeChannel);
            if (fromChannel.isPresent()) {
                return fromChannel;
            }
        }
        if (!Files.isRegularFile(normalized)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(normalized, StandardCharsets.UTF_8);
            return parseMetadata(content);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static synchronized Optional<PidFileMetadata> readMetadataFromChannel(FileChannel fc) {
        try {
            long size = fc.size();
            if (size <= 0 || size > 8192) {
                return Optional.empty();
            }
            ByteBuffer buf = ByteBuffer.allocate((int) size);
            int read = fc.read(buf, 0L);
            if (read <= 0) {
                return Optional.empty();
            }
            String content = new String(buf.array(), 0, read, StandardCharsets.UTF_8);
            return parseMetadata(content);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    static Optional<PidFileMetadata> parseMetadata(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        long pid = -1L;
        Instant startedAt = Instant.EPOCH;
        String command = "unknown";

        String[] lines = content.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("pid=")) {
                try {
                    pid = Long.parseLong(line.substring(4).trim());
                } catch (NumberFormatException ignored) {
                }
            } else if (line.startsWith("startedAt=")) {
                try {
                    startedAt = Instant.parse(line.substring("startedAt=".length()).trim());
                } catch (Exception ignored) {
                }
            } else if (line.startsWith("command=")) {
                command = line.substring("command=".length()).trim();
            } else if (pid == -1L) {
                // Support plain single-integer `.pid` files written by external tools
                try {
                    pid = Long.parseLong(line);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (pid <= 0) {
            return Optional.empty();
        }
        return Optional.of(new PidFileMetadata(pid, startedAt, command));
    }

    private static PidFileMetadata buildCurrentProcessMetadata(long currentPid) {
        ProcessHandle current = ProcessHandle.current();
        ProcessHandle.Info info = current.info();
        Instant startInstant = info.startInstant().orElseGet(Instant::now);
        String cmd = info.commandLine()
                .or(info::command)
                .orElse("java");
        return new PidFileMetadata(currentPid, startInstant, cmd);
    }

    /**
     * Returns the {@link PidLockResult} associated with this guard.
     */
    public PidLockResult result() {
        return result;
    }

    /**
     * Convenience check for {@code result().acquired()}.
     */
    public boolean isAcquired() {
        return result.acquired() && !closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // Already shutting down
                }
            }
            releaseResourcesQuietly();
        }
    }

    private void releaseResourcesQuietly() {
        if (!result.acquired()) {
            return;
        }
        try {
            releaseLockQuietly(fileLock);
            closeChannelQuietly(channel);
            try {
                Files.deleteIfExists(lockFilePath);
            } catch (IOException ignored) {
            }
        } finally {
            ACTIVE_JVM_LOCK_CHANNELS.remove(lockFilePath);
        }
    }

    private static void releaseLockQuietly(FileLock lock) {
        if (lock != null && lock.isValid()) {
            try {
                lock.release();
            } catch (IOException ignored) {
            }
        }
    }

    private static void closeChannelQuietly(FileChannel fc) {
        if (fc != null && fc.isOpen()) {
            try {
                fc.close();
            } catch (IOException ignored) {
            }
        }
    }
}
