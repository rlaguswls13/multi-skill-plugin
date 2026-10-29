package com.msp.health;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Health check that verifies connectivity via TCP socket ping or HTTP GET/HEAD ping.
 */
public class PingCheck implements HealthCheck {

    public enum Mode {
        TCP,
        HTTP
    }

    private final Mode mode;
    private final String hostOrUrl;
    private final int port;
    private final Duration timeout;
    private final Set<Integer> expectedStatusCodes;

    private PingCheck(Mode mode, String hostOrUrl, int port, Duration timeout, Set<Integer> expectedStatusCodes) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.hostOrUrl = Objects.requireNonNull(hostOrUrl, "hostOrUrl must not be null");
        this.port = port;
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(3);
        this.expectedStatusCodes = expectedStatusCodes != null ? expectedStatusCodes : Set.of(200);
    }

    public static PingCheck tcp(String host, int port, Duration timeout) {
        return new PingCheck(Mode.TCP, host, port, timeout, null);
    }

    public static PingCheck tcp(String host, int port) {
        return tcp(host, port, Duration.ofSeconds(3));
    }

    public static PingCheck http(String url, Duration timeout) {
        return new PingCheck(Mode.HTTP, url, -1, timeout, Set.of(200, 201, 204, 301, 302, 307, 308));
    }

    public static PingCheck http(String url) {
        return http(url, Duration.ofSeconds(3));
    }

    public static PingCheck http(String url, Duration timeout, Set<Integer> expectedStatusCodes) {
        return new PingCheck(Mode.HTTP, url, -1, timeout, expectedStatusCodes);
    }

    @Override
    public HealthResult check() throws Exception {
        return switch (mode) {
            case TCP -> checkTcp();
            case HTTP -> checkHttp();
        };
    }

    private HealthResult checkTcp() {
        int timeoutMs = (int) timeout.toMillis();
        long startNanos = System.nanoTime();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("mode", "TCP");
        details.put("host", hostOrUrl);
        details.put("port", port);
        details.put("timeoutMs", timeoutMs);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hostOrUrl, port), timeoutMs);
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            details.put("latencyMs", latencyMs);
            return HealthResult.up("TCP connection successful: " + hostOrUrl + ":" + port, details, Duration.ofMillis(latencyMs));
        } catch (IOException e) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            details.put("latencyMs", latencyMs);
            details.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return HealthResult.down("TCP connection failed to " + hostOrUrl + ":" + port + " (" + e.getMessage() + ")", details, Duration.ofMillis(latencyMs));
        }
    }

    private HealthResult checkHttp() {
        int timeoutMs = (int) timeout.toMillis();
        long startNanos = System.nanoTime();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("mode", "HTTP");
        details.put("url", hostOrUrl);
        details.put("timeoutMs", timeoutMs);

        try {
            URI uri = URI.create(hostOrUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            details.put("statusCode", responseCode);
            details.put("latencyMs", latencyMs);

            if (expectedStatusCodes.contains(responseCode)) {
                return HealthResult.up("HTTP ping successful: " + responseCode + " " + hostOrUrl, details, Duration.ofMillis(latencyMs));
            } else if (responseCode >= 200 && responseCode < 400) {
                return HealthResult.up("HTTP status " + responseCode + " received", details, Duration.ofMillis(latencyMs));
            } else if (responseCode >= 400 && responseCode < 500) {
                return HealthResult.degraded("HTTP client error " + responseCode + " received", details, Duration.ofMillis(latencyMs));
            } else {
                return HealthResult.down("HTTP server error " + responseCode + " received", details, Duration.ofMillis(latencyMs));
            }
        } catch (IOException e) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            details.put("latencyMs", latencyMs);
            details.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return HealthResult.down("HTTP ping failed: " + hostOrUrl + " (" + e.getMessage() + ")", details, Duration.ofMillis(latencyMs));
        }
    }
}
