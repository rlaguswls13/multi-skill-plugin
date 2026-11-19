package com.msp.signotifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SignalNotifier Tests")
class SignalNotifierTest {

    @Test
    @DisplayName("Dispatches listeners in descending priority order")
    void testPriorityDispatchOrder() {
        try (SignalNotifier notifier = new SignalNotifier()) {
            List<String> executed = Collections.synchronizedList(new ArrayList<>());

            notifier.registerListener("Low", SignalType.SIGTERM, event -> executed.add("LOW"), 1, Duration.ofSeconds(1));
            notifier.registerListener("High", SignalType.SIGTERM, event -> executed.add("HIGH"), 10, Duration.ofSeconds(1));
            notifier.registerListener("Medium", SignalType.SIGTERM, event -> executed.add("MED"), 5, Duration.ofSeconds(1));

            SignalDispatchReport report = notifier.notifySignal(SignalType.SIGTERM);

            assertEquals(3, report.getTotalListeners());
            assertTrue(report.isAllSuccess());
            assertEquals(List.of("HIGH", "MED", "LOW"), executed);
        }
    }

    @Test
    @DisplayName("Dispatches listeners with equal priority in FIFO registration order")
    void testFifoTieBreaking() {
        try (SignalNotifier notifier = new SignalNotifier()) {
            List<String> executed = Collections.synchronizedList(new ArrayList<>());

            notifier.registerListener("First", SignalType.SIGINT, event -> executed.add("1"), 5, Duration.ofSeconds(1));
            notifier.registerListener("Second", SignalType.SIGINT, event -> executed.add("2"), 5, Duration.ofSeconds(1));
            notifier.registerListener("Third", SignalType.SIGINT, event -> executed.add("3"), 5, Duration.ofSeconds(1));

            SignalDispatchReport report = notifier.notifySignal(SignalType.SIGINT);

            assertTrue(report.isAllSuccess());
            assertEquals(List.of("1", "2", "3"), executed);
        }
    }

    @Test
    @DisplayName("Enforces per-listener timeout and isolates hanging listeners")
    void testTimeoutEnforcement() {
        try (SignalNotifier notifier = new SignalNotifier()) {
            List<String> executed = Collections.synchronizedList(new ArrayList<>());

            // Fast high-priority listener
            notifier.registerListener("FastPre", SignalType.SIGTERM, event -> executed.add("PRE"), 10, Duration.ofSeconds(1));

            // Hanging listener with 40ms timeout
            notifier.registerListener("Hanger", SignalType.SIGTERM, event -> {
                Thread.sleep(300);
                executed.add("HANG");
            }, 5, Duration.ofMillis(40));

            // Fast low-priority listener
            notifier.registerListener("FastPost", SignalType.SIGTERM, event -> executed.add("POST"), 1, Duration.ofSeconds(1));

            SignalDispatchReport report = notifier.notifySignal(SignalType.SIGTERM);

            assertEquals(3, report.getTotalListeners());
            assertEquals(2, report.getSuccessCount());
            assertEquals(1, report.getTimeoutCount());
            assertFalse(report.isAllSuccess());

            // Hanging listener timed out, but following listeners still executed!
            assertEquals(List.of("PRE", "POST"), executed);
        }
    }

    @Test
    @DisplayName("Isolates listener exceptions and reports FAILED status")
    void testExceptionIsolation() {
        try (SignalNotifier notifier = new SignalNotifier()) {
            AtomicBoolean postExecuted = new AtomicBoolean(false);

            notifier.registerListener("Failing", SignalType.SIGHUP, event -> {
                throw new IllegalStateException("Config file parse error");
            }, 10, Duration.ofSeconds(1));

            notifier.registerListener("Resilient", SignalType.SIGHUP, event -> {
                postExecuted.set(true);
            }, 5, Duration.ofSeconds(1));

            SignalDispatchReport report = notifier.notifySignal(SignalType.SIGHUP);

            assertEquals(2, report.getTotalListeners());
            assertEquals(1, report.getFailedCount());
            assertEquals(1, report.getSuccessCount());
            assertTrue(postExecuted.get());

            ListenerExecutionResult failedResult = report.results().get(0);
            assertEquals(SignalExecutionStatus.FAILED, failedResult.status());
            assertInstanceOf(IllegalStateException.class, failedResult.error());
            assertEquals("Config file parse error", failedResult.error().getMessage());
        }
    }

    @Test
    @DisplayName("Unregistering a listener removes it from future dispatch")
    void testUnregisterListener() {
        try (SignalNotifier notifier = new SignalNotifier()) {
            AtomicBoolean called = new AtomicBoolean(false);
            ListenerRegistration reg = notifier.registerListener(SignalType.SIGINT, event -> called.set(true));

            assertTrue(reg.unregister());

            SignalDispatchReport report = notifier.notifySignal(SignalType.SIGINT);
            assertEquals(0, report.getTotalListeners());
            assertFalse(called.get());
        }
    }

    @Test
    @DisplayName("Listeners only receive signals matching their registered SignalType")
    void testSignalFiltering() {
        try (SignalNotifier notifier = new SignalNotifier()) {
            AtomicBoolean termCalled = new AtomicBoolean(false);
            AtomicBoolean hupCalled = new AtomicBoolean(false);

            notifier.registerListener(SignalType.SIGTERM, event -> termCalled.set(true));
            notifier.registerListener(SignalType.SIGHUP, event -> hupCalled.set(true));

            notifier.notifySignal(SignalType.SIGHUP);

            assertFalse(termCalled.get());
            assertTrue(hupCalled.get());
        }
    }

    @Test
    @DisplayName("Shutdown hook enable and disable lifecycle")
    void testShutdownHookToggle() {
        try (SignalNotifier notifier = new SignalNotifier()) {
            assertFalse(notifier.isShutdownHookEnabled());

            notifier.enableShutdownHook();
            assertTrue(notifier.isShutdownHookEnabled());

            notifier.disableShutdownHook();
            assertFalse(notifier.isShutdownHookEnabled());
        }
    }
}
