package com.msp.anycastprobe;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/**
 * Production TCP Socket connector that measures real network connect handshake latency.
 */
public class DefaultSocketConnector implements SocketConnector {

    @Override
    public Duration measureConnectRtt(String host, int port, Duration timeout) throws IOException {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            int timeoutMillis = (int) Math.max(1, timeout.toMillis());
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
        }
        long elapsedNanos = System.nanoTime() - start;
        return Duration.ofNanos(elapsedNanos);
    }
}
