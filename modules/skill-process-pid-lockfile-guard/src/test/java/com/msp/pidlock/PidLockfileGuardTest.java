package com.msp.pidlock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PidLockfileGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void acquiresLockWritesMetadataAndCleansUpOnClose() throws Exception {
        Path pidFile = tempDir.resolve("service.pid");
        long currentPid = ProcessHandle.current().pid();

        try (PidLockfileGuard guard = PidLockfileGuard.tryAcquire(pidFile)) {
            PidLockResult res = guard.result();
            assertTrue(res.acquired());
            assertTrue(guard.isAcquired());
            assertEquals(currentPid, res.holderPid());
            assertEquals(pidFile.toAbsolutePath().normalize(), res.lockFilePath());
            assertFalse(res.staleRecovered());
            assertTrue(Files.exists(pidFile));

            Optional<PidFileMetadata> metadata = PidLockfileGuard.readMetadataLossless(pidFile);
            assertTrue(metadata.isPresent());
            assertEquals(currentPid, metadata.get().pid());
            assertNotNull(metadata.get().startedAt());
            assertFalse(metadata.get().command().isBlank());
        }

        // After close(), the .pid file must be deleted cleanly
        assertFalse(Files.exists(pidFile));
    }

    @Test
    void preventsDuplicateAcquisitionWhileLockIsHeld() throws Exception {
        Path pidFile = tempDir.resolve("singleton.pid");
        long currentPid = ProcessHandle.current().pid();

        try (PidLockfileGuard first = PidLockfileGuard.tryAcquire(pidFile)) {
            assertTrue(first.result().acquired());

            try (PidLockfileGuard second = PidLockfileGuard.tryAcquire(pidFile)) {
                PidLockResult secondRes = second.result();
                assertFalse(secondRes.acquired());
                assertEquals(currentPid, secondRes.holderPid());
                assertFalse(secondRes.staleRecovered());
            }

            // First lock is still active and file still exists
            assertTrue(first.isAcquired());
            assertTrue(Files.exists(pidFile));
        }

        // Now that first is closed, a new guard can acquire the lock
        try (PidLockfileGuard third = PidLockfileGuard.tryAcquire(pidFile)) {
            assertTrue(third.result().acquired());
        }
    }

    @Test
    void recoversStaleLockfileWhenRecordedPidIsNotAlive() throws Exception {
        Path pidFile = tempDir.resolve("stale-daemon.pid");
        // Write a non-existent dead PID into the .pid file without holding an OS FileLock
        long deadPid = 999_999_999L;
        Files.writeString(
                pidFile,
                "pid=" + deadPid + "\nstartedAt=2025-01-01T00:00:00Z\ncommand=dead-daemon\n",
                StandardCharsets.UTF_8
        );

        assertFalse(PidLockfileGuard.isProcessAlive(deadPid));

        long currentPid = ProcessHandle.current().pid();
        try (PidLockfileGuard guard = PidLockfileGuard.tryAcquire(pidFile)) {
            PidLockResult res = guard.result();
            assertTrue(res.acquired());
            assertEquals(currentPid, res.holderPid());
            assertTrue(res.staleRecovered(), "Should mark staleRecovered = true when reclaiming dead PID lockfile");

            // Verify file content was updated to currentPid
            Optional<PidFileMetadata> updated = PidLockfileGuard.readMetadataLossless(pidFile);
            assertTrue(updated.isPresent());
            assertEquals(currentPid, updated.get().pid());
        }

        assertFalse(Files.exists(pidFile));
    }

    @Test
    void recoversPlainNumericStalePidFile() throws Exception {
        Path pidFile = tempDir.resolve("plain.pid");
        Files.writeString(pidFile, "888888888\n", StandardCharsets.UTF_8);

        try (PidLockfileGuard guard = PidLockfileGuard.tryAcquire(pidFile)) {
            assertTrue(guard.result().acquired());
            assertTrue(guard.result().staleRecovered());
            assertEquals(ProcessHandle.current().pid(), guard.result().holderPid());
        }
    }
}
