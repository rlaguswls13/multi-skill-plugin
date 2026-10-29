package com.msp.heapdump;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility for triggering on-demand JVM heap dumps into standard .hprof files.
 * <p>
 * Interacts with HotSpotDiagnosticMXBean via JMX Platform MBeanServer or reflection fallback.
 * Thread-safe and zero external runtime dependencies.
 */
public final class HeapDumper {

    private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";
    private static final String HOTSPOT_CLASS_NAME = "com.sun.management.HotSpotDiagnosticMXBean";
    private static final DateTimeFormatter FILE_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private HeapDumper() {
    }

    /**
     * Checks if HotSpotDiagnosticMXBean is available on the current JVM platform.
     *
     * @return true if heap dump generation is supported
     */
    public static boolean isAvailable() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName(HOTSPOT_BEAN_NAME);
            if (server.isRegistered(name)) {
                return true;
            }
        } catch (Exception ignored) {
        }

        try {
            Class<?> clazz = Class.forName(HOTSPOT_CLASS_NAME);
            Class<? extends java.lang.management.PlatformManagedObject> pmoClass =
                    clazz.asSubclass(java.lang.management.PlatformManagedObject.class);
            Object bean = ManagementFactory.getPlatformMXBean(pmoClass);
            return bean != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Dumps the heap to an auto-generated timestamped file inside the specified directory.
     * Defaults to liveOnly = true.
     *
     * @param outputDirectory directory where the .hprof file will be saved
     * @return absolute path of the generated heap dump file
     */
    public static Path dumpHeap(Path outputDirectory) {
        return dumpHeap(outputDirectory, true);
    }

    /**
     * Dumps the heap to an auto-generated timestamped file inside the specified directory.
     *
     * @param outputDirectory directory where the .hprof file will be saved
     * @param liveOnly if true, only live reachable objects are dumped (triggers GC)
     * @return absolute path of the generated heap dump file
     */
    public static Path dumpHeap(Path outputDirectory, boolean liveOnly) {
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        String fileName = generateTimestampedFileName();
        Path targetFile = outputDirectory.resolve(fileName);
        return dumpHeap(targetFile, liveOnly, false);
    }

    /**
     * Dumps the heap to the specified file path. Defaults to liveOnly = true.
     *
     * @param filePath destination file path
     * @return absolute path of the generated heap dump file
     */
    public static Path dumpHeap(String filePath) {
        return dumpHeap(filePath, true);
    }

    /**
     * Dumps the heap to the specified file path.
     *
     * @param filePath destination file path
     * @param liveOnly if true, only live reachable objects are dumped (triggers GC)
     * @return absolute path of the generated heap dump file
     */
    public static Path dumpHeap(String filePath, boolean liveOnly) {
        Objects.requireNonNull(filePath, "filePath must not be null");
        return dumpHeap(Path.of(filePath), liveOnly, true);
    }

    /**
     * Dumps the heap to the target path with configurable overwrite policy.
     *
     * @param targetFile target path for the .hprof file
     * @param liveOnly if true, only live reachable objects are dumped (triggers GC)
     * @param overwrite if true, deletes existing file before dumping
     * @return absolute path of the generated heap dump file
     */
    public static Path dumpHeap(Path targetFile, boolean liveOnly, boolean overwrite) {
        Objects.requireNonNull(targetFile, "targetFile must not be null");
        Path absoluteTarget = targetFile.toAbsolutePath().normalize();

        try {
            Path parent = absoluteTarget.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (Files.exists(absoluteTarget)) {
                if (overwrite) {
                    Files.delete(absoluteTarget);
                } else {
                    throw new HeapDumpException("Heap dump destination already exists: " + absoluteTarget);
                }
            }

            invokeDump(absoluteTarget.toString(), liveOnly);

            if (!Files.exists(absoluteTarget)) {
                throw new HeapDumpException("Heap dump finished without creating target file: " + absoluteTarget);
            }

            return absoluteTarget;
        } catch (HeapDumpException e) {
            throw e;
        } catch (IOException e) {
            throw new HeapDumpException("I/O error during heap dump file preparation: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new HeapDumpException("Failed to dump heap to " + absoluteTarget + ": " + e.getMessage(), e);
        }
    }

    /**
     * Generates a unique timestamped file name for heap dump (e.g. heapdump-20261030-040510-123.hprof).
     */
    public static String generateTimestampedFileName() {
        String timestamp = LocalDateTime.now().format(FILE_TS_FORMATTER);
        int seq = COUNTER.getAndIncrement() % 10000;
        return String.format("heapdump-%s-%04d.hprof", timestamp, seq);
    }

    private static void invokeDump(String targetFilePath, boolean liveOnly) throws Exception {
        // Approach 1: JMX Platform MBeanServer invoke
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName(HOTSPOT_BEAN_NAME);
            if (server.isRegistered(name)) {
                server.invoke(
                        name,
                        "dumpHeap",
                        new Object[]{targetFilePath, liveOnly},
                        new String[]{"java.lang.String", "boolean"}
                );
                return;
            }
        } catch (Exception ignored) {
            // Fallback to reflection approach
        }

        // Approach 2: Reflection on com.sun.management.HotSpotDiagnosticMXBean
        try {
            Class<?> clazz = Class.forName(HOTSPOT_CLASS_NAME);
            Class<? extends java.lang.management.PlatformManagedObject> pmoClass =
                    clazz.asSubclass(java.lang.management.PlatformManagedObject.class);
            Object bean = ManagementFactory.getPlatformMXBean(pmoClass);
            if (bean != null) {
                Method method = clazz.getMethod("dumpHeap", String.class, boolean.class);
                method.invoke(bean, targetFilePath, liveOnly);
                return;
            }
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException(
                    "HotSpotDiagnosticMXBean is not supported on current JVM runtime: " + System.getProperty("java.vm.name"), e);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new HeapDumpException("HotSpotDiagnosticMXBean dump invocation failed: " + cause.getMessage(), cause);
        }

        throw new UnsupportedOperationException(
                "Unable to find or register HotSpotDiagnosticMXBean on current JVM: " + System.getProperty("java.vm.name"));
    }
}
