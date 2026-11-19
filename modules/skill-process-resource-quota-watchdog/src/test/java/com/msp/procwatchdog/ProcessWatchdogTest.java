package com.msp.procwatchdog;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class ProcessWatchdogTest {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

    private final ProcessWatchdog watchdog = new ProcessWatchdog();

    private static List<String> shellCommand(String cmd) {
        return IS_WINDOWS
                ? List.of("cmd.exe", "/c", cmd)
                : List.of("sh", "-c", cmd);
    }

    private static List<String> javaChildCommand(String mode) {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", IS_WINDOWS ? "java.exe" : "java").toString();
        String classpath = System.getProperty("java.class.path");
        return List.of(javaBin, "-cp", classpath, TestChildProcess.class.getName(), mode);
    }

    @Test
    void shellCommandCompletesSuccessfullyAcrossOs() throws Exception {
        WatchdogPolicy policy = WatchdogPolicy.builder()
                .maxWallTime(Duration.ofSeconds(10))
                .maxOutputBytes(64 * 1024)
                .build();

        WatchdogResult result = watchdog.execute(shellCommand("echo hello-watchdog"), policy);

        assertTrue(result.isCompleted());
        assertEquals(TerminationReason.COMPLETED, result.terminationReason());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdoutUtf8().contains("hello-watchdog"), "Unexpected stdout: " + result.stdoutUtf8());
    }

    @Test
    void shellCommandCapturesNonZeroExitCode() throws Exception {
        WatchdogPolicy policy = WatchdogPolicy.builder()
                .maxWallTime(Duration.ofSeconds(10))
                .build();

        WatchdogResult result = watchdog.execute(shellCommand("exit 7"), policy);

        assertEquals(TerminationReason.COMPLETED, result.terminationReason());
        assertEquals(7, result.exitCode());
    }

    @Test
    void capturesBothStdoutAndStderrCleanly() throws Exception {
        WatchdogPolicy policy = WatchdogPolicy.builder()
                .maxWallTime(Duration.ofSeconds(10))
                .build();

        WatchdogResult result = watchdog.execute(javaChildCommand("both"), policy);

        assertEquals(TerminationReason.COMPLETED, result.terminationReason());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdoutUtf8().contains("stdout-line"));
        assertTrue(result.stderrUtf8().contains("stderr-line"));
        assertTrue(result.totalOutputBytes() > 0);
    }

    @Test
    void enforcesWallClockTimeoutAndTerminatesProcess() throws Exception {
        WatchdogPolicy policy = WatchdogPolicy.builder()
                .maxWallTime(Duration.ofMillis(450))
                .maxIdleTime(Duration.ofSeconds(5))
                .destroyGracePeriod(Duration.ofMillis(150))
                .build();

        WatchdogResult result = watchdog.execute(javaChildCommand("heartbeat"), policy);

        assertEquals(TerminationReason.WALL_TIMEOUT, result.terminationReason());
        assertFalse(result.isCompleted());
        assertTrue(result.elapsed().toMillis() < 8_000, "Process should be terminated promptly on wall timeout");
    }

    @Test
    void enforcesIdleOutputTimeout() throws Exception {
        WatchdogPolicy policy = WatchdogPolicy.builder()
                .maxWallTime(Duration.ofSeconds(15))
                .maxIdleTime(Duration.ofMillis(400))
                .destroyGracePeriod(Duration.ofMillis(150))
                .build();

        WatchdogResult result = watchdog.execute(javaChildCommand("idle"), policy);

        assertEquals(TerminationReason.IDLE_TIMEOUT, result.terminationReason());
        assertTrue(result.stdoutUtf8().contains("started"));
        assertTrue(result.elapsed().toMillis() < 8_000, "Process should be terminated promptly on idle timeout");
    }

    @Test
    void enforcesOutputByteQuotaAndCapsCapturedMemory() throws Exception {
        long quota = 4096L;
        WatchdogPolicy policy = WatchdogPolicy.builder()
                .maxWallTime(Duration.ofSeconds(15))
                .maxOutputBytes(quota)
                .destroyGracePeriod(Duration.ofMillis(150))
                .build();

        WatchdogResult result = watchdog.execute(javaChildCommand("flood"), policy);

        assertEquals(TerminationReason.OUTPUT_QUOTA_EXCEEDED, result.terminationReason());
        assertTrue(result.stdout().length + result.stderr().length <= quota,
                "Captured output bytes must not exceed quota");
        assertTrue(result.totalOutputBytes() > quota);
    }
}
