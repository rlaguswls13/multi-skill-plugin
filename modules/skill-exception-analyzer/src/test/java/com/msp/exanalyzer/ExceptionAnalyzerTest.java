package com.msp.exanalyzer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionAnalyzerTest {

    @Test
    @DisplayName("Extract root cause and cause chain from nested exceptions")
    void testRootCauseAndChain() {
        IllegalStateException root = new IllegalStateException("Database connection pool exhausted");
        IOException middle = new IOException("Failed to fetch connection", root);
        RuntimeException top = new RuntimeException("Service request failed", middle);

        Throwable rootFound = ExceptionAnalyzer.getRootCause(top);
        assertEquals(root, rootFound);

        List<Throwable> chain = ExceptionAnalyzer.getCauseChain(top);
        assertEquals(3, chain.size());
        assertEquals(top, chain.get(0));
        assertEquals(middle, chain.get(1));
        assertEquals(root, chain.get(2));
    }

    @Test
    @DisplayName("Cycle-safe cause chain traversal handles circular references")
    void testCyclicCauseProtection() {
        Exception e1 = new Exception("Error 1");
        Exception e2 = new Exception("Error 2", e1);

        // Circular loop: e1 -> e2 -> e1
        e1.initCause(e2);

        List<Throwable> chain = ExceptionAnalyzer.getCauseChain(e1);
        assertEquals(2, chain.size());
        assertEquals(e1, chain.get(0));
        assertEquals(e2, chain.get(1));

        Throwable root = ExceptionAnalyzer.getRootCause(e1);
        assertEquals(e2, root);
    }

    @Test
    @DisplayName("Find specific cause type and type matching")
    void testFindCauseAndMatches() {
        IllegalArgumentException root = new IllegalArgumentException("invalid id");
        RuntimeException top = new RuntimeException("wrapper", root);

        assertTrue(ExceptionAnalyzer.matches(top, IllegalArgumentException.class));
        assertFalse(ExceptionAnalyzer.matches(top, IOException.class));

        Optional<IllegalArgumentException> found = ExceptionAnalyzer.findCause(top, IllegalArgumentException.class);
        assertTrue(found.isPresent());
        assertEquals("invalid id", found.get().getMessage());
    }

    @Test
    @DisplayName("Detects network errors")
    void testNetworkErrorDetection() {
        ConnectException conn = new ConnectException("Connection refused: localhost:8080");
        RuntimeException wrapper = new RuntimeException("API Call failed", conn);

        assertTrue(ExceptionAnalyzer.isNetworkError(wrapper));
        assertEquals(ExceptionCategory.NETWORK, ExceptionAnalyzer.categorize(wrapper));

        IOException msgBased = new IOException("broken pipe to downstream service");
        assertTrue(ExceptionAnalyzer.isNetworkError(msgBased));
    }

    @Test
    @DisplayName("Detects timeout errors")
    void testTimeoutErrorDetection() {
        SocketTimeoutException sockTimeout = new SocketTimeoutException("Read timed out");
        RuntimeException wrapper = new RuntimeException(sockTimeout);

        assertTrue(ExceptionAnalyzer.isTimeout(wrapper));
        assertEquals(ExceptionCategory.TIMEOUT, ExceptionAnalyzer.categorize(wrapper));

        TimeoutException directTimeout = new TimeoutException("operation timed out after 5000ms");
        assertTrue(ExceptionAnalyzer.isTimeout(directTimeout));
    }

    @Test
    @DisplayName("Detects security and authorization errors")
    void testSecurityErrorDetection() {
        AccessDeniedException accessDenied = new AccessDeniedException("/var/data/secret.key");
        RuntimeException wrapper = new RuntimeException("File access denied", accessDenied);

        assertTrue(ExceptionAnalyzer.isSecurityError(wrapper));
        assertEquals(ExceptionCategory.SECURITY, ExceptionAnalyzer.categorize(wrapper));

        SecurityException secEx = new SecurityException("unauthorized access to admin resource");
        assertTrue(ExceptionAnalyzer.isSecurityError(secEx));
    }

    @Test
    @DisplayName("Detects validation errors")
    void testValidationErrorDetection() {
        IllegalArgumentException argEx = new IllegalArgumentException("username must not be empty");
        RuntimeException wrapper = new RuntimeException("Invalid payload", argEx);

        assertTrue(ExceptionAnalyzer.isValidationError(wrapper));
        assertEquals(ExceptionCategory.VALIDATION, ExceptionAnalyzer.categorize(wrapper));
    }

    @Test
    @DisplayName("Stack trace condensation collapses internal JDK frames and tags app frames")
    void testCondenseStackTrace() {
        Exception ex = new Exception("Sample failure");
        StackTraceElement[] trace = new StackTraceElement[]{
                new StackTraceElement("com.msp.order.OrderService", "process", "OrderService.java", 42),
                new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 568),
                new StackTraceElement("jdk.internal.reflect.DirectMethodHandleAccessor", "invoke", "DirectMethodHandleAccessor.java", 103),
                new StackTraceElement("org.junit.platform.runner.TestRunner", "run", "TestRunner.java", 20),
                new StackTraceElement("com.msp.common.Runner", "main", "Runner.java", 10)
        };
        ex.setStackTrace(trace);

        List<CondensedFrame> frames = ExceptionAnalyzer.condense(ex, Set.of("com.msp"));

        assertEquals(3, frames.size());
        assertInstanceOf(CondensedFrame.AppFrame.class, frames.get(0));
        assertInstanceOf(CondensedFrame.OmittedFrames.class, frames.get(1));
        CondensedFrame.OmittedFrames omitted = (CondensedFrame.OmittedFrames) frames.get(1);
        assertEquals(3, omitted.count());
        assertInstanceOf(CondensedFrame.AppFrame.class, frames.get(2));

        String formatted = ExceptionAnalyzer.formatCondensedStackTrace(ex, Set.of("com.msp"));
        assertTrue(formatted.contains("java.lang.Exception: Sample failure"));
        assertTrue(formatted.contains("com.msp.order.OrderService.process(OrderService.java:42) [APP]"));
        assertTrue(formatted.contains("... 3 omitted frames (JDK / Reflection)"));
        assertTrue(formatted.contains("com.msp.common.Runner.main(Runner.java:10) [APP]"));
    }
}
