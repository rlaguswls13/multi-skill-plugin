package com.msp.ping;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Objects;

/**
 * Pure Java 21 network ping and TCP port probe utility.
 */
public final class NetworkPingProbe {

    public static final int DEFAULT_TIMEOUT_MILLIS = 3000;

    private NetworkPingProbe() {
    }

    /**
     * Performs a TCP handshake probe to the target host and port within the given timeout.
     *
     * @param host          target hostname or IP address
     * @param port          target port number (1 - 65535)
     * @param timeoutMillis socket connect timeout in milliseconds
     * @return PingResult representing connection status and round-trip time
     */
    public static PingResult pingTcp(String host, int port, int timeoutMillis) {
        Objects.requireNonNull(host, "host must not be null");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535: " + port);
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be positive: " + timeoutMillis);
        }

        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            long rtt = (System.nanoTime() - start) / 1_000_000L;
            return PingResult.success(host, port, rtt);
        } catch (IOException e) {
            long rtt = (System.nanoTime() - start) / 1_000_000L;
            String msg = (e.getMessage() != null && !e.getMessage().isBlank()) ? e.getMessage() : e.getClass().getSimpleName();
            return PingResult.failure(host, port, rtt, msg);
        }
    }

    /**
     * Performs TCP probe using default 3000ms timeout.
     */
    public static PingResult pingTcp(String host, int port) {
        return pingTcp(host, port, DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * Probes host reachability using OS-level ICMP/echo via {@link InetAddress#isReachable(int)}.
     *
     * @param host          target hostname or IP address
     * @param timeoutMillis timeout in milliseconds
     * @return PingResult with port set to 0
     */
    public static PingResult pingIcmp(String host, int timeoutMillis) {
        Objects.requireNonNull(host, "host must not be null");
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be positive: " + timeoutMillis);
        }

        long start = System.nanoTime();
        try {
            InetAddress address = InetAddress.getByName(host);
            boolean reachable = address.isReachable(timeoutMillis);
            long rtt = (System.nanoTime() - start) / 1_000_000L;
            if (reachable) {
                return PingResult.success(host, 0, rtt);
            } else {
                return PingResult.failure(host, 0, rtt, "Host unreachable within " + timeoutMillis + "ms");
            }
        } catch (IOException e) {
            long rtt = (System.nanoTime() - start) / 1_000_000L;
            String msg = (e.getMessage() != null && !e.getMessage().isBlank()) ? e.getMessage() : e.getClass().getSimpleName();
            return PingResult.failure(host, 0, rtt, msg);
        }
    }

    /**
     * Probes host reachability using default 3000ms timeout.
     */
    public static PingResult pingIcmp(String host) {
        return pingIcmp(host, DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * Performs batch TCP probes across multiple ports on the same host.
     *
     * @param host          target hostname or IP address
     * @param ports         list of ports to probe
     * @param timeoutMillis timeout per probe in milliseconds
     * @return list of PingResult objects corresponding to each port
     */
    public static List<PingResult> probePorts(String host, List<Integer> ports, int timeoutMillis) {
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(ports, "ports must not be null");

        return ports.stream()
                .map(port -> pingTcp(host, port, timeoutMillis))
                .toList();
    }

    /**
     * Performs batch TCP probes using default 3000ms timeout.
     */
    public static List<PingResult> probePorts(String host, List<Integer> ports) {
        return probePorts(host, ports, DEFAULT_TIMEOUT_MILLIS);
    }
}
