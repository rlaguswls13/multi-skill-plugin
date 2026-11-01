package com.msp.prockill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessTreeKillerTest {

    @Test
    @DisplayName("SafeMode prevents killing PID 0, PID 1, and current JVM PID")
    void testSafeModeProtection() {
        ProcessTreeKiller killer = ProcessTreeKiller.create();

        assertThrows(IllegalArgumentException.class, () -> killer.execute(0));
        assertThrows(IllegalArgumentException.class, () -> killer.execute(-5));
        assertThrows(IllegalArgumentException.class, () -> killer.execute(1));

        long selfPid = ProcessHandle.current().pid();
        assertThrows(IllegalArgumentException.class, () -> killer.execute(selfPid));
    }

    @Test
    @DisplayName("Dry run mode inspects process tree without sending termination signals")
    void testDryRunInspection() {
        long selfPid = ProcessHandle.current().pid();

        ProcessKillReport report = ProcessTreeKiller.create()
                .dryRun(true)
                .allowKillCurrentProcess(true)
                .execute(selfPid);

        assertTrue(report.dryRun());
        assertEquals(selfPid, report.targetPid());
        assertFalse(report.terminatedGracefully().isEmpty());
        assertTrue(report.killedForcibly().isEmpty());
        assertTrue(report.failed().isEmpty());

        // Process should still be alive
        assertTrue(ProcessHandle.of(selfPid).map(ProcessHandle::isAlive).orElse(false));
    }

    @Test
    @DisplayName("InspectTree convenience method returns process hierarchy")
    void testInspectTree() {
        long selfPid = ProcessHandle.current().pid();
        List<ProcessInfo> hierarchy = ProcessTreeKiller.inspectTree(selfPid);

        assertNotNull(hierarchy);
        assertFalse(hierarchy.isEmpty());
        assertTrue(hierarchy.stream().anyMatch(p -> p.pid() == selfPid));
    }

    @Test
    @DisplayName("Gracefully handles non-existent PID")
    void testNonExistentPid() {
        long nonExistentPid = 999_999_999L;
        ProcessKillReport report = ProcessTreeKiller.kill(nonExistentPid);

        assertEquals(nonExistentPid, report.targetPid());
        assertTrue(report.terminatedGracefully().isEmpty());
        assertTrue(report.killedForcibly().isEmpty());
        assertTrue(report.failed().isEmpty());
        assertTrue(report.isSuccessful());
    }

    @Test
    @DisplayName("Successfully terminates a live spawned process tree")
    void testTerminateSpawnedProcess() throws IOException, InterruptedException {
        // Start a long-running child process
        ProcessBuilder pb = isWindows()
                ? new ProcessBuilder("powershell", "-Command", "Start-Sleep -Seconds 60")
                : new ProcessBuilder("sleep", "60");

        Process process = pb.start();
        assertTrue(process.isAlive());

        long pid = process.pid();

        ProcessKillReport report = ProcessTreeKiller.create()
                .gracePeriod(Duration.ofSeconds(2))
                .execute(process);

        assertFalse(process.isAlive());
        assertTrue(report.isSuccessful());
        assertTrue(report.totalAffected() >= 1);
        assertTrue(report.terminatedGracefully().stream().anyMatch(p -> p.pid() == pid)
                || report.killedForcibly().stream().anyMatch(p -> p.pid() == pid));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
