package com.msp.forwarded;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-performance parser and normalizer for RFC 7239 {@code Forwarded} headers
 * and legacy {@code X-Forwarded-*} proxy headers.
 * <p>
 * Zero 3rd-party runtime dependencies. Thread-safe and stateless utility methods.
 */
public final class ForwardedHeaderParser {

    private ForwardedHeaderParser() {
        // Utility class
    }

    /**
     * Parses an RFC 7239 {@code Forwarded} header into a list of {@link ForwardedElement}s.
     * <p>
     * Handles multiple hops separated by commas, quoted strings, escaped characters,
     * bracketed IPv6 addresses, and case-insensitive parameter names.
     *
     * @param header the raw {@code Forwarded} header value
     * @return unmodifiable list of parsed elements; empty list if input is null or blank
     */
    public static List<ForwardedElement> parseRfc7239(String header) {
        if (header == null || header.isBlank()) {
            return Collections.emptyList();
        }

        List<ForwardedElement> result = new ArrayList<>();
        List<String> hopStrings = splitTokens(header, ',');

        for (String hopStr : hopStrings) {
            String trimmedHop = hopStr.trim();
            if (trimmedHop.isEmpty()) {
                continue;
            }

            List<String> paramStrings = splitTokens(trimmedHop, ';');
            String by = null;
            String forAddress = null;
            String host = null;
            String proto = null;
            Map<String, String> extensions = new LinkedHashMap<>();

            for (String paramStr : paramStrings) {
                int eqIdx = paramStr.indexOf('=');
                if (eqIdx == -1) {
                    continue; // Malformed pair without '='
                }
                String rawKey = paramStr.substring(0, eqIdx).trim().toLowerCase();
                String rawVal = paramStr.substring(eqIdx + 1).trim();
                String unescapedVal = unescapeValue(rawVal);

                switch (rawKey) {
                    case "by" -> by = unescapedVal;
                    case "for" -> forAddress = unescapedVal;
                    case "host" -> host = unescapedVal;
                    case "proto" -> proto = unescapedVal;
                    default -> {
                        if (!rawKey.isEmpty()) {
                            extensions.put(rawKey, unescapedVal);
                        }
                    }
                }
            }

            if (by != null || forAddress != null || host != null || proto != null || !extensions.isEmpty()) {
                result.add(new ForwardedElement(by, forAddress, host, proto, extensions));
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Synthesizes {@link ForwardedElement}s from legacy {@code X-Forwarded-*} headers.
     *
     * @param xForwardedFor   Comma-separated client and proxy IPs (e.g. "192.0.2.43, 198.51.100.17")
     * @param xForwardedProto Protocol or comma-separated protocols (e.g. "https" or "https, http")
     * @param xForwardedHost  Host or comma-separated hosts (e.g. "example.com")
     * @param xForwardedPort  Port or comma-separated ports (e.g. "443" or "8080")
     * @return unmodifiable list of converted elements; empty list if all inputs are null or blank
     */
    public static List<ForwardedElement> parseLegacy(
            String xForwardedFor,
            String xForwardedProto,
            String xForwardedHost,
            String xForwardedPort
    ) {
        List<String> forList = splitCommaList(xForwardedFor);
        List<String> protoList = splitCommaList(xForwardedProto);
        List<String> hostList = splitCommaList(xForwardedHost);
        List<String> portList = splitCommaList(xForwardedPort);

        if (forList.isEmpty() && protoList.isEmpty() && hostList.isEmpty() && portList.isEmpty()) {
            return Collections.emptyList();
        }

        int count = Math.max(forList.size(), 1);
        List<ForwardedElement> result = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String forAddr = getOrFallback(forList, i);
            String proto = getOrFallback(protoList, i);
            String rawHost = getOrFallback(hostList, i);
            String port = getOrFallback(portList, i);

            String combinedHost = rawHost;
            if (combinedHost != null && port != null && !combinedHost.contains(":")) {
                combinedHost = combinedHost + ":" + port;
            }

            Map<String, String> ext = new LinkedHashMap<>();
            if (port != null && (combinedHost == null || !combinedHost.endsWith(":" + port))) {
                ext.put("port", port);
            }

            result.add(new ForwardedElement(null, forAddr, combinedHost, proto, ext));
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Extracts the outermost client IP safely, inspecting RFC 7239 first, then falling back to
     * {@code X-Forwarded-For}.
     *
     * @param rfc7239Header  RFC 7239 {@code Forwarded} header value (may be null)
     * @param xForwardedFor  Legacy {@code X-Forwarded-For} header value (may be null)
     * @return canonical client IP without bracket/port decorations, or {@code null} if not found
     */
    public static String getClientIp(String rfc7239Header, String xForwardedFor) {
        if (rfc7239Header != null && !rfc7239Header.isBlank()) {
            List<ForwardedElement> elements = parseRfc7239(rfc7239Header);
            for (ForwardedElement el : elements) {
                String ip = el.clientIp();
                if (ip != null && !ip.isBlank()) {
                    return ip;
                }
            }
        }

        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            List<String> ips = splitCommaList(xForwardedFor);
            for (String raw : ips) {
                if (!raw.isBlank()) {
                    ForwardedElement dummy = new ForwardedElement(null, raw, null, null);
                    String ip = dummy.clientIp();
                    if (ip != null && !ip.isBlank()) {
                        return ip;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Serializes a list of {@link ForwardedElement}s into a standardized RFC 7239 header string.
     *
     * @param elements list of elements to serialize
     * @return formatted RFC 7239 header string (e.g. {@code "for=192.0.2.43;proto=https, for=198.51.100.17"})
     */
    public static String toRfc7239(List<ForwardedElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        boolean firstHop = true;

        for (ForwardedElement el : elements) {
            if (el == null) {
                continue;
            }
            if (!firstHop) {
                sb.append(", ");
            }
            firstHop = false;

            List<String> pairs = new ArrayList<>();
            if (el.by() != null) {
                pairs.add("by=" + formatValue(el.by()));
            }
            if (el.forAddress() != null) {
                pairs.add("for=" + formatValue(el.forAddress()));
            }
            if (el.host() != null) {
                pairs.add("host=" + formatValue(el.host()));
            }
            if (el.proto() != null) {
                pairs.add("proto=" + formatValue(el.proto()));
            }
            if (el.extensions() != null) {
                for (Map.Entry<String, String> entry : el.extensions().entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        pairs.add(entry.getKey() + "=" + formatValue(entry.getValue()));
                    }
                }
            }

            sb.append(String.join(";", pairs));
        }

        return sb.toString();
    }

    private static String formatValue(String value) {
        if (value == null) {
            return "\"\"";
        }
        if (isToken(value)) {
            return value;
        }
        // Quoted string per RFC 7239
        StringBuilder sb = new StringBuilder(value.length() + 8);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    private static boolean isToken(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!isTchar(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTchar(char c) {
        return (c >= 'a' && c <= 'z') ||
               (c >= 'A' && c <= 'Z') ||
               (c >= '0' && c <= '9') ||
               c == '!' || c == '#' || c == '$' || c == '%' || c == '&' ||
               c == '\'' || c == '*' || c == '+' || c == '-' || c == '.' ||
               c == '^' || c == '_' || c == '`' || c == '|' || c == '~';
    }

    private static String unescapeValue(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length()) {
                    i++;
                    sb.append(s.charAt(i));
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return s;
    }

    private static List<String> splitTokens(String input, char delimiter) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\' && inQuotes) {
                sb.append(c);
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
                sb.append(c);
            } else if (c == delimiter && !inQuotes) {
                list.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0 || !list.isEmpty()) {
            list.add(sb.toString());
        }
        return list;
    }

    private static List<String> splitCommaList(String val) {
        if (val == null || val.isBlank()) {
            return Collections.emptyList();
        }
        List<String> rawTokens = splitTokens(val, ',');
        List<String> result = new ArrayList<>(rawTokens.size());
        for (String t : rawTokens) {
            String trimmed = t.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }
            result.add(trimmed);
        }
        return result;
    }

    private static String getOrFallback(List<String> list, int index) {
        if (list.isEmpty()) {
            return null;
        }
        if (index < list.size()) {
            return list.get(index);
        }
        return list.get(0); // fallback to single header value if proxy collapsed it
    }
}
