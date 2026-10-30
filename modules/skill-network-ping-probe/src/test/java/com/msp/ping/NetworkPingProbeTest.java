package com.msp.ping;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NetworkPingProbeTest {

    @Test
    @DisplayName("TCP probe against active local ServerSocket succeeds")
    void testTcpProbeSuccess() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            int port = server.getLocalPort();
            assertTrue(port > 0);

            PingResult result = NetworkPingProbe.pingTcp("127.0.0.1", port, 2000);

            assertTrue(result.reachable());
            assertEquals("127.0.0.1", result.host());
            assertEquals(port, result.port());
            assertTrue(result.rttMillis() >= 0);
            assertNull(result.errorMessage());
        }
    }

    @Test
    @DisplayName("TCP probe against closed port fails with error message")
    void testTcpProbeFailure() throws IOException {
        int closedPort;
        try (ServerSocket temp = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            closedPort = temp.getLocalPort();
        } // server is closed now

        PingResult result = NetworkPingProbe.pingTcp("127.0.0.1", closedPort, 500);

        assertFalse(result.reachable());
        assertEquals("127.0.0.1", result.host());
        assertEquals(closedPort, result.port());
        assertNotNull(result.errorMessage());
        assertFalse(result.errorMessage().isBlank());
    }

    @Test
    @DisplayName("ICMP/Echo probe against localhost succeeds")
    void testIcmpProbeLocalhost() {
        PingResult result = NetworkPingProbe.pingIcmp("127.0.0.1", 2000);

        assertTrue(result.reachable());
        assertEquals("127.0.0.1", result.host());
        assertEquals(0, result.port());
        assertTrue(result.rttMillis() >= 0);
        assertNull(result.errorMessage());
    }

    @Test
    @DisplayName("Batch probe ports against multiple ports")
    void testBatchPortProbe() throws IOException {
        int openPort;
        int closedPort;

        try (ServerSocket s1 = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))) {
            openPort = s1.getLocalPort();

            try (ServerSocket s2 = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                closedPort = s2.getLocalPort();
            } // closedPort closed

            List<PingResult> results = NetworkPingProbe.probePorts("127.0.0.1", List.of(openPort, closedPort), 1000);

            assertEquals(2, results.size());
            assertTrue(results.get(0).reachable());
            assertEquals(openPort, results.get(0).port());

            assertFalse(results.get(1).reachable());
            assertEquals(closedPort, results.get(1).port());
        }
    }

    @Test
    @DisplayName("Validation checks for invalid inputs")
    void testInputValidation() {
        assertThrows(NullPointerException.class, () -> NetworkPingProbe.pingTcp(null, 80));
        assertThrows(IllegalArgumentException.class, () -> NetworkPingProbe.pingTcp("127.0.0.1", 0));
        assertThrows(IllegalArgumentException.class, () -> NetworkPingProbe.pingTcp("127.0.0.1", 70000));
        assertThrows(IllegalArgumentException.class, () -> NetworkPingProbe.pingTcp("127.0.0.1", 80, 0));
        assertThrows(IllegalArgumentException.class, () -> NetworkPingProbe.pingTcp("127.0.0.1", 80, -10));

        assertThrows(NullPointerException.class, () -> NetworkPingProbe.pingIcmp(null));
        assertThrows(IllegalArgumentException.class, () -> NetworkPingProbe.pingIcmp("127.0.0.1", 0));
    }
}
