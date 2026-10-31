package com.msp.processpipe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessPipelineTest {

    private final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    private List<String> echoCommand(String text) {
        if (isWindows) {
            return List.of("cmd.exe", "/c", "echo", text);
        } else {
            return List.of("echo", text);
        }
    }

    private List<String> multilineProducerCommand() {
        if (isWindows) {
            return List.of("cmd.exe", "/c", "echo line1& echo matchedTarget& echo line3");
        } else {
            return List.of("sh", "-c", "printf 'line1\\nmatchedTarget\\nline3\\n'");
        }
    }

    private List<String> filterCommand(String target) {
        if (isWindows) {
            return List.of("findstr", target);
        } else {
            return List.of("grep", target);
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
    @DisplayName("Single command execution successfully runs and captures output")
    void testSingleCommandExecution() {
        ProcessPipeline pipeline = ProcessPipeline.create()
                .stage(echoCommand("hello-pipeline"));

        PipelineResult result = pipeline.execute(Duration.ofSeconds(5));

        assertTrue(result.isSuccess(), "Pipeline should succeed");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello-pipeline"));
        assertNotNull(result.duration());
    }

    @Test
    @DisplayName("2-Stage piped execution connects stage 1 stdout to stage 2 stdin")
    void testTwoStagePipedExecution() {
        ProcessPipeline pipeline = ProcessPipeline.create()
                .stage(multilineProducerCommand())
                .stage(filterCommand("matchedTarget"));

        PipelineResult result = pipeline.execute(Duration.ofSeconds(5));

        assertTrue(result.isSuccess(), "2-stage pipeline should succeed");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("matchedTarget"), "Stdout must contain target line");
        assertFalse(result.stdout().contains("line1"), "Stdout must have filtered out non-target line");
    }

    @Test
    @DisplayName("Timeout handling forcibly terminates processes and flags failure")
    void testTimeoutHandlingAndProcessDestruction() {
        ProcessPipeline pipeline = ProcessPipeline.create()
                .stage(sleepCommand(5));

        PipelineResult result = pipeline.execute(Duration.ofMillis(350));

        assertFalse(result.isSuccess(), "Timed out pipeline must not be successful");
        assertEquals(-1, result.exitCode());
        assertTrue(result.stderr().contains("timed out"), "Stderr should note timeout: " + result.stderr());
        assertTrue(result.duration().toMillis() >= 300);
    }

    @Test
    @DisplayName("Error exit code from failing command is correctly captured")
    void testErrorExitCodeCapture() {
        ProcessPipeline pipeline = ProcessPipeline.create()
                .stage(exitCodeCommand(42));

        PipelineResult result = pipeline.execute(Duration.ofSeconds(5));

        assertFalse(result.isSuccess());
        assertEquals(42, result.exitCode());
    }

    @Test
    @DisplayName("Environment and working directory propagation across stages")
    void testEnvironmentAndWorkingDirectory() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));

        List<String> printEnv;
        if (isWindows) {
            printEnv = List.of("cmd.exe", "/c", "echo %CUSTOM_PIPE_KEY%");
        } else {
            printEnv = List.of("sh", "-c", "echo $CUSTOM_PIPE_KEY");
        }

        ProcessPipeline pipeline = ProcessPipeline.create()
                .workingDir(tempDir)
                .environment(Map.of("CUSTOM_PIPE_KEY", "VAL_9999"))
                .stage(printEnv);

        PipelineResult result = pipeline.execute(Duration.ofSeconds(5));

        assertTrue(result.isSuccess());
        assertTrue(result.stdout().contains("VAL_9999"));
    }

    @Test
    @DisplayName("Validation for pipeline stages and timeouts")
    void testValidation() {
        ProcessPipeline emptyPipeline = ProcessPipeline.create();
        assertThrows(IllegalStateException.class, () -> emptyPipeline.execute(Duration.ofSeconds(1)));

        ProcessPipeline valid = ProcessPipeline.create().stage(echoCommand("test"));
        assertThrows(NullPointerException.class, () -> valid.execute(null));
        assertThrows(IllegalArgumentException.class, () -> valid.execute(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> valid.execute(Duration.ofSeconds(-1)));
    }
}
