package com.msp.anycastprobe;

import java.io.IOException;
import java.time.Duration;

/**
 * Interface abstraction for measuring TCP socket handshake latency.
 * Enables deterministic mocking for unit tests without network access.
 */
@FunctionalInterface
public interface SocketConnector {

    /**
     * Connects to the target host and port and measures the connection handshake duration.
     *
     * @param host    hostname or IP address
     * @param port    target port
     * @param timeout maximum allowed duration for connection
     * @return measured round-trip time duration
     * @throws IOException if connection fails or times out
     */
    Duration measureConnectRtt(String host, int port, Duration timeout) throws IOException;
}
