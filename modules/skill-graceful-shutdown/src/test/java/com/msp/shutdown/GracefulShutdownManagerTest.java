package com.msp.shutdown;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GracefulShutdownManager Unit Tests")
class GracefulShutdownManagerTest {

    @Test
    @DisplayName("Shutdown executes components in strict phase order")
    void testOrderedShutdown() {
        GracefulShutdownManager manager = new GracefulShutdownManager();
        List<String> auditTrail = Collections.synchronizedList(new ArrayList<>());

        // Register in mixed order
        manager.registerComponent("Database-Pool", 3, () -> auditTrail.add("DB-Closed"));
        manager.registerComponent("Web-Server", 1, () -> auditTrail.add("Web-Stopped"));
        manager.registerComponent("Message-Queue-Worker", 2, () -> auditTrail.add("MQ-Drained"));

        assertEquals(3, manager.registeredCount());

        ShutdownReport report = manager.initiateShutdown(Duration.ofSeconds(5));

        assertTrue(report.isSuccessful());
        assertFalse(report.isTimedOut());
        assertEquals(3, report.componentResults().size());

        // Verify phase order: 1 -> 2 -> 3
        assertEquals(List.of("Web-Stopped", "MQ-Drained", "DB-Closed"), auditTrail);
    }

    @Test
    @DisplayName("Component exceptions do not halt teardown of subsequent phases")
    void testExceptionHandlingDoesNotHaltShutdown() {
        GracefulShutdownManager manager = new GracefulShutdownManager();
        AtomicBoolean dbClosed = new AtomicBoolean(false);

        manager.registerComponent("Failing-Cache", 1, () -> {
            throw new IOException("Simulated network failure on cache flush");
        });
        manager.registerComponent("Database", 2, () -> dbClosed.set(true));

        ShutdownReport report = manager.initiateShutdown(Duration.ofSeconds(2));

        assertFalse(report.isSuccessful(), "Report should indicate failure");
        assertFalse(report.isTimedOut());
        assertTrue(dbClosed.get(), "Database in Phase 2 must still be closed even if Phase 1 had an error");

        assertEquals(1, report.exceptions().size());
        assertInstanceOf(IOException.class, report.exceptions().get(0));

        ComponentShutdownResult cacheResult = report.resultMap().get("Failing-Cache");
        assertNotNull(cacheResult);
        assertFalse(cacheResult.isSuccess());

        ComponentShutdownResult dbResult = report.resultMap().get("Database");
        assertNotNull(dbResult);
        assertTrue(dbResult.isSuccess());
    }

    @Test
    @DisplayName("Timeout enforcement bounds total shutdown time")
    void testTimeoutEnforcement() {
        GracefulShutdownManager manager = new GracefulShutdownManager();

        manager.registerComponent("Hanging-Component", 1, () -> {
            Thread.sleep(3000); // Exceeds timeout
        });
        manager.registerComponent("Subsequent-Component", 2, () -> {});

        long start = System.currentTimeMillis();
        ShutdownReport report = manager.initiateShutdown(Duration.ofMillis(250));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 1500, "Shutdown must terminate promptly around timeout deadline");
        assertTrue(report.isTimedOut());
        assertFalse(report.isSuccessful());

        ComponentShutdownResult hanging = report.resultMap().get("Hanging-Component");
        assertNotNull(hanging);
        assertFalse(hanging.isSuccess());
    }

    @Test
    @DisplayName("Idempotency: multiple initiate calls return same report")
    void testIdempotentShutdown() {
        GracefulShutdownManager manager = new GracefulShutdownManager();
        AtomicBoolean closed = new AtomicBoolean(false);

        manager.registerComponent("Service", 1, () -> closed.set(true));

        ShutdownReport report1 = manager.initiateShutdown(Duration.ofSeconds(2));
        ShutdownReport report2 = manager.initiateShutdown(Duration.ofSeconds(2));

        assertTrue(closed.get());
        assertSame(report1, report2);
        assertTrue(manager.isShutdownInitiated());
        assertTrue(manager.isShutdownCompleted());

        // Subsequent registrations should fail
        assertThrows(IllegalStateException.class, () -> manager.registerComponent("New", 1, () -> {}));
    }

    @Test
    @DisplayName("Deregistration and shutdown hook registration safety")
    void testDeregistrationAndHook() {
        GracefulShutdownManager manager = new GracefulShutdownManager();
        manager.registerComponent("Temp", 1, () -> {});
        assertEquals(1, manager.registeredCount());

        boolean removed = manager.deregisterComponent("Temp");
        assertTrue(removed);
        assertEquals(0, manager.registeredCount());

        boolean hookAdded = manager.addShutdownHook(Duration.ofSeconds(5));
        assertTrue(hookAdded);

        // Cannot add twice
        assertFalse(manager.addShutdownHook());

        boolean hookRemoved = manager.removeShutdownHook();
        assertTrue(hookRemoved);
    }
}
