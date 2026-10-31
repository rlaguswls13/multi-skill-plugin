package com.msp.processrunner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Safe Process Runner Tests")
class SafeProcessRunnerTest {

    private final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    private List<String> echoCommand(String message) {
        if (isWindows) {
            return List.of("cmd.exe", "/c", "echo", message);
        } else {
            return List.of("echo", message);
        }
    }

    private List<String> sleepCommand(int seconds) {
        if (isWindows) {
            return List.of("powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds " + seconds);
        } else {
            return List.of("sleep", String.valueOf(seconds));
        }
    }

    private List<String> exitCodeCommand(int code) {
        if (isWindows) {
            return List.of("cmd.exe", "/c", "exit", String.valueOf(code));
        } else {
            return List.of("sh", "-c", "exit " + code);
        }
    }

    @Test
    @DisplayName("Normal process execution successfully captures stdout")
    void testSuccessfulExecution() {
        ProcessRunResult result = SafeProcessRunner.run(echoCommand("hello-safe-runner"), Duration.ofSeconds(5));

        assertTrue(result.isSuccess());
        assertEquals(0, result.exitCode());
        assertFalse(result.timedOut());
        assertTrue(result.stdout().contains("hello-safe-runner"));
        assertNotNull(result.executionTime());
    }

    @Test
    @DisplayName("Non-zero exit code process execution")
    void testNonZeroExitCode() {
        ProcessRunResult result = SafeProcessRunner.run(exitCodeCommand(42), Duration.ofSeconds(5));

        assertFalse(result.isSuccess());
        assertEquals(42, result.exitCode());
        assertFalse(result.timedOut());
    }

    @Test
    @DisplayName("Timeout guarantees process kill and returns timedOut=true")
    void testProcessTimeout() {
        // Run a 5-second sleep command with a short 300ms timeout
        ProcessRunResult result = SafeProcessRunner.run(sleepCommand(5), Duration.ofMillis(300));

        assertTrue(result.timedOut(), "Process must be flagged as timed out");
        assertFalse(result.isSuccess());
        assertEquals(-1, result.exitCode());
        assertTrue(result.executionTime().toMillis() >= 250);
    }

    @Test
    @DisplayName("Environment variable propagation to child process")
    void testEnvironmentVariables() {
        List<String> cmd;
        if (isWindows) {
            cmd = List.of("cmd.exe", "/c", "echo %CUSTOM_MSP_VAR%");
        } else {
            cmd = List.of("sh", "-c", "echo $CUSTOM_MSP_VAR");
        }

        ProcessRunResult result = SafeProcessRunner.run(
                cmd,
                null,
                Map.of("CUSTOM_MSP_VAR", "MSP_SECRET_2026"),
                Duration.ofSeconds(5)
        );

        assertTrue(result.isSuccess());
        assertTrue(result.stdout().contains("MSP_SECRET_2026"));
    }

    @Test
    @DisplayName("Working directory configuration")
    void testWorkingDirectory() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        List<String> cmd;
        if (isWindows) {
            cmd = List.of("cmd.exe", "/c", "cd");
        } else {
            cmd = List.of("pwd");
        }

        ProcessRunResult result = SafeProcessRunner.run(cmd, tempDir, null, Duration.ofSeconds(5));
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("Output buffer bounds prevent OOM while avoiding deadlock")
    void testMaxOutputBufferBounding() {
        // Produce 10KB of output but limit captured output to 128 bytes
        List<String> cmd;
        if (isWindows) {
            cmd = List.of("powershell", "-NoProfile", "-Command", "1..200 | ForEach-Object { 'LineOfText-' + $_ }");
        } else {
            cmd = List.of("sh", "-c", "for i in $(seq 1 200); do echo \"LineOfText-$i\"; done");
        }

        int maxBytes = 128;
        ProcessRunResult result = SafeProcessRunner.run(cmd, null, null, Duration.ofSeconds(10), maxBytes);

        assertTrue(result.isSuccess());
        assertTrue(result.stdout().getBytes().length <= maxBytes + 8); // allows slight boundary tolerance
    }

    @Test
    @DisplayName("Validation for invalid arguments")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () -> SafeProcessRunner.run(null, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> SafeProcessRunner.run(List.of(), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> SafeProcessRunner.run(List.of("echo"), null));
        assertThrows(IllegalArgumentException.class, () -> SafeProcessRunner.run(List.of("echo"), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> SafeProcessRunner.run(List.of("echo"), Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> SafeProcessRunner.run(List.of("echo"), null, null, Duration.ofSeconds(1), 0));
    }
}
