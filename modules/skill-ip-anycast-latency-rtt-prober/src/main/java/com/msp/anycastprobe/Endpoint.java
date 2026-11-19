package com.msp.anycastprobe;

import java.util.Objects;

/**
 * Represents a network POP (Point of Presence) or IP Anycast endpoint.
 *
 * @param name   friendly name (e.g. "tokyo-edge-01")
 * @param host   IP address or hostname
 * @param port   target TCP port (e.g. 443 or 80)
 * @param region optional region identifier (e.g. "ap-northeast-1")
 */
public record Endpoint(String name, String host, int port, String region) {

    public Endpoint {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(host, "host must not be null");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in 1..65535: " + port);
        }
    }

    public Endpoint(String name, String host, int port) {
        this(name, host, port, "global");
    }

    @Override
    public String toString() {
        return name + " (" + host + ":" + port + " [" + region + "])";
    }
}
