package com.msp.forwarded;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Represents a single parsed proxy hop element in an RFC 7239 {@code Forwarded} header
 * or synthesized from legacy {@code X-Forwarded-*} headers.
 *
 * @param by          The interface where the request came in to the proxy (e.g. IP, obfuscated id).
 * @param forAddress The entity that initiated the request or previous proxy (e.g. IPv4, bracketed IPv6, port).
 * @param host        The host request header field received by the proxy.
 * @param proto       The protocol (e.g. "http", "https") received by the proxy.
 * @param extensions  Additional extension parameters defined in the header.
 */
public record ForwardedElement(
        String by,
        String forAddress,
        String host,
        String proto,
        Map<String, String> extensions
) {
    public ForwardedElement {
        if (extensions == null || extensions.isEmpty()) {
            extensions = Collections.emptyMap();
        } else {
            extensions = Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
        }
    }

    public ForwardedElement(String by, String forAddress, String host, String proto) {
        this(by, forAddress, host, proto, Collections.emptyMap());
    }

    /**
     * Extracts pure IP address from {@link #forAddress()}, stripping bracket formatting
     * (e.g. {@code "[2001:db8::1]:8080"} -&gt; {@code "2001:db8::1"}) or IPv4 port suffixes.
     *
     * @return clean IP address string, or {@code null} if {@code forAddress} is null.
     */
    public String forIp() {
        return extractIp(forAddress);
    }

    /**
     * Alias for {@link #forIp()} representing client IP.
     */
    public String clientIp() {
        return forIp();
    }

    /**
     * Extracts port from {@link #forAddress()} if present.
     *
     * @return {@link OptionalInt} containing the port, or empty if no port is specified.
     */
    public OptionalInt forPort() {
        return extractPort(forAddress);
    }

    /**
     * Extracts pure IP from {@link #by()} if present.
     */
    public String byIp() {
        return extractIp(by);
    }

    /**
     * Extracts port from {@link #by()} if present.
     */
    public OptionalInt byPort() {
        return extractPort(by);
    }

    /**
     * Indicates whether {@link #forAddress()} represents an IPv6 address.
     */
    public boolean isForIpv6() {
        if (forAddress == null) {
            return false;
        }
        String s = forAddress.trim();
        return s.startsWith("[") || (s.chars().filter(ch -> ch == ':').count() >= 2);
    }

    private static String extractIp(String address) {
        if (address == null) {
            return null;
        }
        String s = address.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1).trim();
        }

        // Case 1: Bracketed IPv6, e.g. "[2001:db8::1]" or "[2001:db8::1]:8080"
        if (s.startsWith("[")) {
            int closeBracket = s.indexOf(']');
            if (closeBracket > 0) {
                return s.substring(1, closeBracket);
            }
        }

        // Case 2: IPv4 with port or raw IPv6
        int colonFirst = s.indexOf(':');
        int colonLast = s.lastIndexOf(':');
        if (colonFirst != -1) {
            if (colonFirst == colonLast) {
                // Exactly one colon: IPv4:port
                return s.substring(0, colonFirst);
            } else {
                // Multiple colons: Unbracketed raw IPv6
                return s;
            }
        }

        // Case 3: Plain IPv4 or token/id (e.g. "_secret", "unknown")
        return s;
    }

    private static OptionalInt extractPort(String address) {
        if (address == null) {
            return OptionalInt.empty();
        }
        String s = address.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1).trim();
        }

        if (s.startsWith("[")) {
            int closeBracket = s.indexOf(']');
            if (closeBracket > 0 && closeBracket + 1 < s.length() && s.charAt(closeBracket + 1) == ':') {
                try {
                    int port = Integer.parseInt(s.substring(closeBracket + 2));
                    if (port >= 0 && port <= 65535) {
                        return OptionalInt.of(port);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return OptionalInt.empty();
        }

        int colonFirst = s.indexOf(':');
        int colonLast = s.lastIndexOf(':');
        if (colonFirst != -1 && colonFirst == colonLast) {
            try {
                int port = Integer.parseInt(s.substring(colonFirst + 1));
                if (port >= 0 && port <= 65535) {
                    return OptionalInt.of(port);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return OptionalInt.empty();
    }
}
