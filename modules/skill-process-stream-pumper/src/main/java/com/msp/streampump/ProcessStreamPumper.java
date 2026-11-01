package com.msp.streampump;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Asynchronous stream pumper transferring process stdout/stderr without heap OOM
 * or pipe deadlocks using Java 21 Virtual Threads.
 */
public final class ProcessStreamPumper {

    public static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final AtomicLong THREAD_ID = new AtomicLong(1);

    private ProcessStreamPumper() {
        // Utility class
    }

    /**
     * Asynchronously transfers all bytes from InputStream to OutputStream with custom buffer size.
     *
     * @param in         source input stream
     * @param out        destination output stream
     * @param bufferSize buffer size in bytes (> 0)
     * @return CompletableFuture holding the StreamPumpResult
     */
    public static CompletableFuture<StreamPumpResult> pumpAsync(InputStream in, OutputStream out, int bufferSize) {
        Objects.requireNonNull(in, "in must not be null");
        Objects.requireNonNull(out, "out must not be null");
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be positive: " + bufferSize);
        }

        CompletableFuture<StreamPumpResult> future = new CompletableFuture<>();
        Thread.ofVirtual().name("stream-pumper-" + THREAD_ID.getAndIncrement()).start(() -> {
            long start = System.nanoTime();
            long totalBytes = 0;
            byte[] buffer = new byte[bufferSize];
            try {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    totalBytes += read;
                }
                out.flush();
                Duration duration = Duration.ofNanos(System.nanoTime() - start);
                future.complete(StreamPumpResult.success(totalBytes, duration));
            } catch (Throwable t) {
                Duration duration = Duration.ofNanos(System.nanoTime() - start);
                future.complete(StreamPumpResult.failure(totalBytes, duration, t));
            }
        });

        return future;
    }

    /**
     * Asynchronously transfers all bytes from InputStream to OutputStream using default 8KB buffer.
     *
     * @param in  source input stream
     * @param out destination output stream
     * @return CompletableFuture holding the StreamPumpResult
     */
    public static CompletableFuture<StreamPumpResult> pumpAsync(InputStream in, OutputStream out) {
        return pumpAsync(in, out, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Asynchronously transfers all bytes from InputStream to a destination file.
     * The target file is created or truncated if it already exists.
     *
     * @param in              source input stream
     * @param destinationFile destination path
     * @param bufferSize      buffer size in bytes
     * @return CompletableFuture holding the StreamPumpResult
     */
    public static CompletableFuture<StreamPumpResult> pumpToFileAsync(InputStream in, Path destinationFile, int bufferSize) {
        Objects.requireNonNull(in, "in must not be null");
        Objects.requireNonNull(destinationFile, "destinationFile must not be null");
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be positive: " + bufferSize);
        }

        CompletableFuture<StreamPumpResult> future = new CompletableFuture<>();
        Thread.ofVirtual().name("stream-pumper-file-" + THREAD_ID.getAndIncrement()).start(() -> {
            long start = System.nanoTime();
            long totalBytes = 0;
            try {
                if (destinationFile.getParent() != null) {
                    Files.createDirectories(destinationFile.getParent());
                }
                try (OutputStream fileOut = Files.newOutputStream(destinationFile,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buffer = new byte[bufferSize];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        fileOut.write(buffer, 0, read);
                        totalBytes += read;
                    }
                    fileOut.flush();
                }
                Duration duration = Duration.ofNanos(System.nanoTime() - start);
                future.complete(StreamPumpResult.success(totalBytes, duration));
            } catch (Throwable t) {
                Duration duration = Duration.ofNanos(System.nanoTime() - start);
                future.complete(StreamPumpResult.failure(totalBytes, duration, t));
            }
        });

        return future;
    }

    /**
     * Asynchronously transfers all bytes from InputStream to a destination file using default 8KB buffer.
     *
     * @param in              source input stream
     * @param destinationFile destination path
     * @return CompletableFuture holding the StreamPumpResult
     */
    public static CompletableFuture<StreamPumpResult> pumpToFileAsync(InputStream in, Path destinationFile) {
        return pumpToFileAsync(in, destinationFile, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Asynchronously reads text lines from InputStream and delivers them to a consumer callback.
     *
     * @param in           source input stream
     * @param charset      character encoding
     * @param lineConsumer consumer invoked for each read line
     * @return CompletableFuture holding the StreamPumpResult
     */
    public static CompletableFuture<StreamPumpResult> pumpToLineConsumer(
            InputStream in, Charset charset, Consumer<String> lineConsumer) {
        Objects.requireNonNull(in, "in must not be null");
        Objects.requireNonNull(charset, "charset must not be null");
        Objects.requireNonNull(lineConsumer, "lineConsumer must not be null");

        CompletableFuture<StreamPumpResult> future = new CompletableFuture<>();
        Thread.ofVirtual().name("stream-pumper-lines-" + THREAD_ID.getAndIncrement()).start(() -> {
            long start = System.nanoTime();
            long totalBytes = 0;
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset));
                String line;
                while ((line = reader.readLine()) != null) {
                    lineConsumer.accept(line);
                    totalBytes += line.getBytes(charset).length + 1; // approx including newline
                }
                Duration duration = Duration.ofNanos(System.nanoTime() - start);
                future.complete(StreamPumpResult.success(totalBytes, duration));
            } catch (Throwable t) {
                Duration duration = Duration.ofNanos(System.nanoTime() - start);
                future.complete(StreamPumpResult.failure(totalBytes, duration, t));
            }
        });

        return future;
    }

    /**
     * Asynchronously reads UTF-8 text lines from InputStream and delivers them to a consumer callback.
     *
     * @param in           source input stream
     * @param lineConsumer consumer invoked for each read line
     * @return CompletableFuture holding the StreamPumpResult
     */
    public static CompletableFuture<StreamPumpResult> pumpToLineConsumer(
            InputStream in, Consumer<String> lineConsumer) {
        return pumpToLineConsumer(in, StandardCharsets.UTF_8, lineConsumer);
    }
}
