package com.msp.tempfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe lifecycle manager for temporary files and directories with AutoCloseable support,
 * recursive directory cleanup, and JVM shutdown hook auto-registration.
 */
public final class TempFileManager {

    private static final Set<TempResource> MANAGED_RESOURCES = ConcurrentHashMap.newKeySet();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(TempFileManager::cleanupAll, "temp-file-manager-shutdown-hook"));
    }

    private TempFileManager() {
        // Utility class
    }

    /**
     * Creates a temporary file in the default temporary-file directory.
     *
     * @param prefix prefix string to be used in generating the file's name
     * @param suffix suffix string to be used in generating the file's name (e.g. ".tmp")
     * @return AutoCloseable TempFile
     */
    public static TempFile createTempFile(String prefix, String suffix) throws IOException {
        Path path = Files.createTempFile(prefix, suffix);
        TempFile tempFile = new TempFile(path, MANAGED_RESOURCES::remove);
        MANAGED_RESOURCES.add(tempFile);
        return tempFile;
    }

    /**
     * Creates a temporary file in the specified directory.
     */
    public static TempFile createTempFile(Path dir, String prefix, String suffix) throws IOException {
        Objects.requireNonNull(dir, "dir must not be null");
        Path path = Files.createTempFile(dir, prefix, suffix);
        TempFile tempFile = new TempFile(path, MANAGED_RESOURCES::remove);
        MANAGED_RESOURCES.add(tempFile);
        return tempFile;
    }

    /**
     * Creates a temporary directory in the default temporary-file directory.
     *
     * @param prefix prefix string to be used in generating the directory's name
     * @return AutoCloseable TempDirectory
     */
    public static TempDirectory createTempDirectory(String prefix) throws IOException {
        Path path = Files.createTempDirectory(prefix);
        TempDirectory tempDir = new TempDirectory(path, MANAGED_RESOURCES::remove);
        MANAGED_RESOURCES.add(tempDir);
        return tempDir;
    }

    /**
     * Creates a temporary directory in the specified directory.
     */
    public static TempDirectory createTempDirectory(Path dir, String prefix) throws IOException {
        Objects.requireNonNull(dir, "dir must not be null");
        Path path = Files.createTempDirectory(dir, prefix);
        TempDirectory tempDir = new TempDirectory(path, MANAGED_RESOURCES::remove);
        MANAGED_RESOURCES.add(tempDir);
        return tempDir;
    }

    /**
     * Registers an existing arbitrary path into the temporary resource lifecycle manager.
     * If the path is a directory, it will be treated as TempDirectory (recursively deleted upon cleanup).
     *
     * @param path target path to register
     * @return registered TempResource
     */
    public static TempResource register(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        TempResource resource;
        if (Files.isDirectory(path)) {
            resource = new TempDirectory(path, MANAGED_RESOURCES::remove);
        } else {
            resource = new TempFile(path, MANAGED_RESOURCES::remove);
        }
        MANAGED_RESOURCES.add(resource);
        return resource;
    }

    /**
     * Closes and cleans up all currently registered temporary resources.
     */
    public static void cleanupAll() {
        for (TempResource resource : MANAGED_RESOURCES) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // Graceful cleanup
            }
        }
        MANAGED_RESOURCES.clear();
    }

    /**
     * Returns the count of currently registered, unclosed temporary resources.
     */
    public static int activeResourceCount() {
        return MANAGED_RESOURCES.size();
    }
}
