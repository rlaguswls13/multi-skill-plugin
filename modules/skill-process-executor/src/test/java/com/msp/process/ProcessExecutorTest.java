package com.msp.process;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ProcessExecutorTest {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String JAVA_BIN = ProcessHandle.current().info().command().orElse("java");

    @Test
    @DisplayName("Should execute basic command and capture output and exit code")
    void shouldExecuteBasicCommand() throws Exception {
        ProcessResult result;
        if (IS_WINDOWS) {
            result = ProcessExecutor.builder()
                    .command("cmd.exe", "/c", "echo", "Hello MSP Process")
                    .build()
                    .execute();
        } else {
            result = ProcessExecutor.builder()
                    .command("echo", "Hello MSP Process")
                    .build()
                    .execute();
        }

        assertTrue(result.isSuccess());
        assertEquals(0, result.exitCode());
        assertFalse(result.isTimedOut());
        assertTrue(result.stdout().contains("Hello MSP Process"));
        assertTrue(result.duration().toMillis() >= 0);
        assertDoesNotThrow(result::assertSuccess);
    }

    @Test
    @DisplayName("Should execute java -version and capture execution")
    void shouldExecuteJavaVersion() throws Exception {
        ProcessResult result = ProcessExecutor.run(JAVA_BIN, "-version");
        assertEquals(0, result.exitCode());
        assertFalse(result.isTimedOut());
        // java -version prints to either stdout or stderr depending on vendor/version
        String combined = result.stdout() + result.stderr();
        assertTrue(combined.contains("version") || combined.contains("Runtime"));
    }

    @Test
    @DisplayName("Should capture non-zero exit code on failure")
    void shouldCaptureNonZeroExitCode() throws Exception {
        ProcessResult result;
        if (IS_WINDOWS) {
            result = ProcessExecutor.run("cmd.exe", "/c", "exit 42");
        } else {
            result = ProcessExecutor.run("sh", "-c", "exit 42");
        }

        assertEquals(42, result.exitCode());
        assertFalse(result.isSuccess());
        assertThrows(IllegalStateException.class, result::assertSuccess);
    }

    @Test
    @DisplayName("Should terminate and report timeout when command exceeds limit")
    void shouldHandleTimeout() throws Exception {
        ProcessResult result;
        Duration timeout = Duration.ofMillis(300);

        if (IS_WINDOWS) {
            result = ProcessExecutor.builder()
                    .command("powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds 5")
                    .timeout(timeout)
                    .build()
                    .execute();
        } else {
            result = ProcessExecutor.builder()
                    .command("sleep", "5")
                    .timeout(timeout)
                    .build()
                    .execute();
        }

        assertTrue(result.isTimedOut(), "Expected process to be marked as timed out");
        assertFalse(result.isSuccess());
        assertTrue(result.duration().toMillis() < 4000, "Should terminate promptly near timeout limit");
    }

    @Test
    @DisplayName("Should pass custom environment variables to child process")
    void shouldPassEnvironmentVariables() throws Exception {
        Map<String, String> env = Map.of("MSP_VAR", "TEST_VALUE_42");
        ProcessResult result;

        if (IS_WINDOWS) {
            result = ProcessExecutor.builder()
                    .command("cmd.exe", "/c", "echo %MSP_VAR%")
                    .environment(env)
                    .build()
                    .execute();
        } else {
            result = ProcessExecutor.builder()
                    .command("sh", "-c", "echo $MSP_VAR")
                    .environment(env)
                    .build()
                    .execute();
        }

        assertTrue(result.isSuccess());
        assertTrue(result.stdout().contains("TEST_VALUE_42"));
    }

    @Test
    @DisplayName("Should execute in specified working directory")
    void shouldExecuteInSpecifiedDirectory(@TempDir Path tempDir) throws Exception {
        ProcessResult result;
        if (IS_WINDOWS) {
            result = ProcessExecutor.builder()
                    .command("cmd.exe", "/c", "cd")
                    .directory(tempDir)
                    .build()
                    .execute();
        } else {
            result = ProcessExecutor.builder()
                    .command("pwd")
                    .directory(tempDir)
                    .build()
                    .execute();
        }

        assertTrue(result.isSuccess());
        String expectedPath = tempDir.toRealPath().toString();
        // Compare case-insensitively or normalizing path
        assertTrue(result.stdout().trim().equalsIgnoreCase(expectedPath) ||
                result.stdout().replace("\\", "/").contains(tempDir.getFileName().toString()));
    }

    @Test
    @DisplayName("Should execute asynchronously via CompletableFuture")
    void shouldExecuteAsynchronously() throws Exception {
        CompletableFuture<ProcessResult> future;
        if (IS_WINDOWS) {
            future = ProcessExecutor.builder()
                    .command("cmd.exe", "/c", "echo Async Hello")
                    .build()
                    .executeAsync();
        } else {
            future = ProcessExecutor.builder()
                    .command("echo", "Async Hello")
                    .build()
                    .executeAsync();
        }

        ProcessResult result = future.get();
        assertTrue(result.isSuccess());
        assertTrue(result.stdout().contains("Async Hello"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when command is empty")
    void shouldThrowWhenCommandIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> ProcessExecutor.builder().build());
    }
}
