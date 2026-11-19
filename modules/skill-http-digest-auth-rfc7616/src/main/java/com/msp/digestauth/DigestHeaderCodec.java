package com.msp.digestauth;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Internal RFC 7616 / RFC 9110 {@code auth-param} parser and serializer for Digest headers.
 */
final class DigestHeaderCodec {
    private static final String SCHEME = "Digest";

    private DigestHeaderCodec() {
    }

    static Map<String, String> parseParams(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new DigestAuthException("Digest header must not be null or blank");
        }
        String trimmed = headerValue.trim();
        if (trimmed.length() < SCHEME.length()
                || !trimmed.regionMatches(true, 0, SCHEME, 0, SCHEME.length())
                || (trimmed.length() > SCHEME.length() && !Character.isWhitespace(trimmed.charAt(SCHEME.length())))) {
            throw new DigestAuthException("Header does not start with 'Digest' scheme");
        }

        Map<String, String> params = new LinkedHashMap<>();
        int i = SCHEME.length();
        int len = trimmed.length();

        while (i < len) {
            while (i < len && (Character.isWhitespace(trimmed.charAt(i)) || trimmed.charAt(i) == ',')) {
                i++;
            }
            if (i >= len) {
                break;
            }

            int keyStart = i;
            while (i < len && trimmed.charAt(i) != '=' && !Character.isWhitespace(trimmed.charAt(i))) {
                i++;
            }
            String key = trimmed.substring(keyStart, i).toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                throw new DigestAuthException("Empty parameter name at index " + keyStart);
            }

            while (i < len && Character.isWhitespace(trimmed.charAt(i))) {
                i++;
            }
            if (i >= len || trimmed.charAt(i) != '=') {
                throw new DigestAuthException("Expected '=' after parameter '" + key + "'");
            }
            i++; // skip '='

            while (i < len && Character.isWhitespace(trimmed.charAt(i))) {
                i++;
            }
            if (i >= len) {
                throw new DigestAuthException("Missing value for parameter '" + key + "'");
            }

            String value;
            if (trimmed.charAt(i) == '"') {
                i++; // skip opening quote
                StringBuilder sb = new StringBuilder();
                boolean closed = false;
                while (i < len) {
                    char c = trimmed.charAt(i++);
                    if (c == '\\') {
                        if (i >= len) {
                            throw new DigestAuthException("Unterminated escape sequence in parameter '" + key + "'");
                        }
                        sb.append(trimmed.charAt(i++));
                    } else if (c == '"') {
                        closed = true;
                        break;
                    } else {
                        sb.append(c);
                    }
                }
                if (!closed) {
                    throw new DigestAuthException("Unterminated quoted string for parameter '" + key + "'");
                }
                value = sb.toString();
            } else {
                int valStart = i;
                while (i < len && trimmed.charAt(i) != ',' && !Character.isWhitespace(trimmed.charAt(i))) {
                    i++;
                }
                value = trimmed.substring(valStart, i);
            }

            if (params.putIfAbsent(key, value) != null) {
                throw new DigestAuthException("Duplicate parameter '" + key + "'");
            }
        }
        return params;
    }

    static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
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
}
