package com.msp.process;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Robust cross-platform OS process executor with timeout handling,
 * asynchronous execution, environment customization, and deadlock-free stream draining.
 */
public final class ProcessExecutor {

    private static final Executor VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final List<String> command;
    private final Path directory;
    private final Map<String, String> environment;
    private final boolean inheritEnvironment;
    private final Duration timeout;
    private final byte[] standardInput;
    private final Charset charset;

    private ProcessExecutor(Builder builder) {
        if (builder.command.isEmpty()) {
            throw new IllegalArgumentException("Process command cannot be empty");
        }
        this.command = List.copyOf(builder.command);
        this.directory = builder.directory;
        this.environment = Collections.unmodifiableMap(new HashMap<>(builder.environment));
        this.inheritEnvironment = builder.inheritEnvironment;
        this.timeout = builder.timeout;
        this.standardInput = builder.standardInput != null ? builder.standardInput.clone() : null;
        this.charset = builder.charset != null ? builder.charset : StandardCharsets.UTF_8;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Executes command directly with default settings.
     */
    public static ProcessResult run(String... command) throws IOException, InterruptedException {
        return builder().command(command).build().execute();
    }

    /**
     * Executes command with the specified timeout.
     */
    public static ProcessResult run(Duration timeout, String... command) throws IOException, InterruptedException {
        return builder().command(command).timeout(timeout).build().execute();
    }

    /**
     * Executes the configured process synchronously.
     *
     * @return ProcessResult containing exit code, stdout, stderr, duration, and timeout status.
     * @throws IOException          if an I/O error occurs starting the process
     * @throws InterruptedException if the current thread is interrupted
     */
    public ProcessResult execute() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);

        if (directory != null) {
            pb.directory(directory.toFile());
        }

        if (!inheritEnvironment) {
            pb.environment().clear();
        }
        if (!environment.isEmpty()) {
            pb.environment().putAll(environment);
        }

        long startNanos = System.nanoTime();
        Process process = pb.start();

        // Feed stdin if configured
        if (standardInput != null && standardInput.length > 0) {
            try (OutputStream os = process.getOutputStream()) {
                os.write(standardInput);
                os.flush();
            }
        } else {
            // Close stdin immediately if unused to avoid blocking processes expecting EOF
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
            }
        }

        // Drain stdout and stderr concurrently to prevent OS buffer pipe deadlocks
        CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                () -> readStreamToString(process.getInputStream(), charset), VIRTUAL_EXECUTOR);
        CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                () -> readStreamToString(process.getErrorStream(), charset), VIRTUAL_EXECUTOR);

        boolean timedOut = false;
        int exitCode;

        try {
            if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
                boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!completed) {
                    timedOut = true;
                    process.destroyForcibly();
                    process.waitFor(3, TimeUnit.SECONDS);
                    exitCode = -1;
                } else {
                    exitCode = process.exitValue();
                }
            } else {
                exitCode = process.waitFor();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        Duration duration = Duration.ofNanos(elapsedNanos);

        String stdout = safeGetStreamResult(stdoutFuture);
        String stderr = safeGetStreamResult(stderrFuture);

        return new ProcessResult(exitCode, stdout, stderr, duration, timedOut);
    }

    /**
     * Executes the process asynchronously using Java virtual threads.
     *
     * @return CompletableFuture completing with ProcessResult
     */
    public CompletableFuture<ProcessResult> executeAsync() {
        return executeAsync(VIRTUAL_EXECUTOR);
    }

    /**
     * Executes the process asynchronously using the supplied executor.
     *
     * @param executor custom executor
     * @return CompletableFuture completing with ProcessResult
     */
    public CompletableFuture<ProcessResult> executeAsync(Executor executor) {
        Objects.requireNonNull(executor, "executor must not be null");
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute();
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new RuntimeException("Process execution failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    private static String readStreamToString(InputStream in, Charset charset) {
        try (in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toString(charset);
        } catch (IOException e) {
            return "";
        }
    }

    private static String safeGetStreamResult(CompletableFuture<String> future) {
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "";
        }
    }

    public static final class Builder {
        private final List<String> command = new ArrayList<>();
        private Path directory;
        private final Map<String, String> environment = new HashMap<>();
        private boolean inheritEnvironment = true;
        private Duration timeout;
        private byte[] standardInput;
        private Charset charset = StandardCharsets.UTF_8;

        private Builder() {
        }

        public Builder command(String... command) {
            Objects.requireNonNull(command, "command array must not be null");
            this.command.clear();
            this.command.addAll(Arrays.asList(command));
            return this;
        }

        public Builder command(List<String> command) {
            Objects.requireNonNull(command, "command list must not be null");
            this.command.clear();
            this.command.addAll(command);
            return this;
        }

        public Builder directory(Path directory) {
            this.directory = directory;
            return this;
        }

        public Builder directory(File directory) {
            this.directory = directory != null ? directory.toPath() : null;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            if (environment != null) {
                this.environment.putAll(environment);
            }
            return this;
        }

        public Builder environment(String key, String value) {
            Objects.requireNonNull(key, "environment key must not be null");
            if (value != null) {
                this.environment.put(key, value);
            } else {
                this.environment.remove(key);
            }
            return this;
        }

        public Builder inheritEnvironment(boolean inheritEnvironment) {
            this.inheritEnvironment = inheritEnvironment;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder standardInput(String input) {
            this.standardInput = input != null ? input.getBytes(charset) : null;
            return this;
        }

        public Builder standardInput(byte[] input) {
            this.standardInput = input != null ? input.clone() : null;
            return this;
        }

        public Builder charset(Charset charset) {
            this.charset = Objects.requireNonNullElse(charset, StandardCharsets.UTF_8);
            return this;
        }

        public ProcessExecutor build() {
            return new ProcessExecutor(this);
        }
    }
}
