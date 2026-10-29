package com.msp.health;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckerTest {

    @Test
    @DisplayName("HealthStatus combines correctly based on severity")
    void testHealthStatusCombine() {
        assertEquals(HealthStatus.UP, HealthStatus.UP.combine(HealthStatus.UP));
        assertEquals(HealthStatus.UNKNOWN, HealthStatus.UP.combine(HealthStatus.UNKNOWN));
        assertEquals(HealthStatus.DEGRADED, HealthStatus.UNKNOWN.combine(HealthStatus.DEGRADED));
        assertEquals(HealthStatus.DOWN, HealthStatus.DEGRADED.combine(HealthStatus.DOWN));
        assertEquals(HealthStatus.DOWN, HealthStatus.DOWN.combine(HealthStatus.UP));

        assertTrue(HealthStatus.UP.isHealthy());
        assertFalse(HealthStatus.DEGRADED.isHealthy());
        assertTrue(HealthStatus.DEGRADED.isOperational());
        assertFalse(HealthStatus.DOWN.isOperational());
    }

    @Test
    @DisplayName("Empty registry produces overall UP status")
    void testEmptyRegistry() {
        try (HealthChecker checker = new HealthChecker()) {
            HealthReport report = checker.checkAll();
            assertEquals(HealthStatus.UP, report.overallStatus());
            assertTrue(report.components().isEmpty());
            assertTrue(report.isHealthy());
        }
    }

    @Test
    @DisplayName("FreeMemoryCheck and DiskSpaceCheck run successfully")
    void testBuiltInResourceChecks() {
        HealthRegistry registry = new HealthRegistry();
        registry.register("memory", FreeMemoryCheck.defaults());
        registry.register("disk", DiskSpaceCheck.ofCurrentDirectory(10, 50));

        try (HealthChecker checker = new HealthChecker(registry)) {
            HealthReport report = checker.checkAll();
            assertNotNull(report);
            assertEquals(2, report.components().size());

            HealthResult memResult = report.components().get("memory");
            assertNotNull(memResult);
            assertTrue(memResult.details().containsKey("usedBytes"));

            HealthResult diskResult = report.components().get("disk");
            assertNotNull(diskResult);
            assertTrue(diskResult.details().containsKey("usableBytes"));

            String json = report.toJson(true);
            assertTrue(json.contains("\"memory\""));
            assertTrue(json.contains("\"disk\""));
            assertTrue(json.contains("\"status\""));
        }
    }

    @Test
    @DisplayName("PingCheck TCP against an open local socket succeeds, closed port fails")
    void testPingCheckTcp() throws Exception {
        int openPort;
        try (ServerSocket server = new ServerSocket(0)) {
            openPort = server.getLocalPort();
            PingCheck openCheck = PingCheck.tcp("127.0.0.1", openPort, Duration.ofMillis(500));
            HealthResult result = openCheck.check();
            assertEquals(HealthStatus.UP, result.status());
        }

        // Port is now closed
        PingCheck closedCheck = PingCheck.tcp("127.0.0.1", openPort, Duration.ofMillis(500));
        HealthResult failResult = closedCheck.check();
        assertEquals(HealthStatus.DOWN, failResult.status());
    }

    @Test
    @DisplayName("PingCheck HTTP against simple HttpServer verifies status code")
    void testPingCheckHttp() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/healthz", exchange -> {
            byte[] response = "OK".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        try {
            PingCheck check = PingCheck.http("http://127.0.0.1:" + port + "/healthz", Duration.ofSeconds(2));
            HealthResult result = check.check();
            assertEquals(HealthStatus.UP, result.status());
            assertEquals(200, result.details().get("statusCode"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Timeout handling cancels slow check and returns DOWN")
    void testCheckTimeout() {
        HealthRegistry registry = new HealthRegistry();
        registry.register("fast", () -> HealthResult.up("Fast check"), Duration.ofSeconds(1));
        registry.register("slow", () -> {
            Thread.sleep(500);
            return HealthResult.up("Never reached");
        }, Duration.ofMillis(100));

        try (HealthChecker checker = new HealthChecker(registry)) {
            HealthReport report = checker.checkAll();
            assertEquals(HealthStatus.DOWN, report.overallStatus());

            HealthResult fastRes = report.components().get("fast");
            assertEquals(HealthStatus.UP, fastRes.status());

            HealthResult slowRes = report.components().get("slow");
            assertEquals(HealthStatus.DOWN, slowRes.status());
            assertTrue(slowRes.message().contains("timed out"));
        }
    }

    @Test
    @DisplayName("SupplierCheck with exception is handled as DOWN")
    void testExceptionHandling() {
        HealthRegistry registry = new HealthRegistry();
        registry.register("failing", () -> {
            throw new RuntimeException("Database connection timeout");
        });

        try (HealthChecker checker = new HealthChecker(registry)) {
            HealthReport report = checker.checkAll();
            assertEquals(HealthStatus.DOWN, report.overallStatus());
            HealthResult res = report.components().get("failing");
            assertEquals(HealthStatus.DOWN, res.status());
            assertTrue(res.message().contains("Database connection timeout"));
        }
    }

    @Test
    @DisplayName("Composite status hierarchy: UP + DEGRADED -> DEGRADED")
    void testDegradedComposite() {
        HealthRegistry registry = new HealthRegistry();
        registry.register("c1", () -> HealthResult.up());
        registry.register("c2", () -> HealthResult.degraded("High latency warning"));

        try (HealthChecker checker = new HealthChecker(registry)) {
            HealthReport report = checker.checkAll();
            assertEquals(HealthStatus.DEGRADED, report.overallStatus());
            assertFalse(report.isHealthy());
            assertTrue(report.isOperational());
        }
    }
}
