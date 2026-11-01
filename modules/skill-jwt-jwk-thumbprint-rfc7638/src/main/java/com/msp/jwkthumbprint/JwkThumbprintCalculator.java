package com.msp.jwkthumbprint;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Pure Java 21 implementation of RFC 7638 JSON Web Key (JWK) Thumbprint calculation.
 * <p>
 * Zero 3rd-party runtime dependencies. Uses standard {@link MessageDigest} and {@link Base64}.
 * Thread-safe utility methods.
 */
public final class JwkThumbprintCalculator {

    private JwkThumbprintCalculator() {
        // Utility class
    }

    /**
     * Computes the RFC 7638 SHA-256 thumbprint for an RSA public key with modulus n and exponent e.
     * <p>
     * Lexicographical canonical JSON: {@code {"e":"...","kty":"RSA","n":"..."}}
     *
     * @param modulusN  Base64URL-encoded modulus n
     * @param exponentE Base64URL-encoded exponent e
     * @return Base64URL-encoded SHA-256 thumbprint without padding
     */
    public static String computeRsaThumbprint(String modulusN, String exponentE) {
        Objects.requireNonNull(modulusN, "modulusN must not be null");
        Objects.requireNonNull(exponentE, "exponentE must not be null");
        if (modulusN.isBlank() || exponentE.isBlank()) {
            throw new IllegalArgumentException("modulusN and exponentE must not be blank");
        }

        String canonical = "{\"e\":\"" + escapeJson(exponentE) + "\",\"kty\":\"RSA\",\"n\":\"" + escapeJson(modulusN) + "\"}";
        byte[] digest = computeSha256Digest(canonical);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    /**
     * Computes the RFC 7638 SHA-256 thumbprint for an RSA public key.
     *
     * @param publicKey the RSA public key
     * @return Base64URL-encoded SHA-256 thumbprint without padding
     */
    public static String computeRsaThumbprint(RSAPublicKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(publicKey.getModulus()));
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(publicKey.getPublicExponent()));
        return computeRsaThumbprint(n, e);
    }

    /**
     * Computes the RFC 7638 SHA-256 thumbprint for an Elliptic Curve (EC) public key.
     * <p>
     * Lexicographical canonical JSON: {@code {"crv":"...","kty":"EC","x":"...","y":"..."}}
     *
     * @param crv curve name (e.g. "P-256", "P-384", "P-521")
     * @param x   Base64URL-encoded x coordinate
     * @param y   Base64URL-encoded y coordinate
     * @return Base64URL-encoded SHA-256 thumbprint without padding
     */
    public static String computeEcThumbprint(String crv, String x, String y) {
        Objects.requireNonNull(crv, "crv must not be null");
        Objects.requireNonNull(x, "x must not be null");
        Objects.requireNonNull(y, "y must not be null");
        if (crv.isBlank() || x.isBlank() || y.isBlank()) {
            throw new IllegalArgumentException("crv, x, and y must not be blank");
        }

        String canonical = "{\"crv\":\"" + escapeJson(crv) + "\",\"kty\":\"EC\",\"x\":\"" + escapeJson(x) + "\",\"y\":\"" + escapeJson(y) + "\"}";
        byte[] digest = computeSha256Digest(canonical);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    /**
     * Parses standard JWK JSON, extracts required members per RFC 7638, and computes the thumbprint.
     *
     * @param jwkJson JSON representation of the JWK
     * @return Base64URL-encoded SHA-256 thumbprint without padding
     */
    public static String computeThumbprintFromJwkJson(String jwkJson) {
        Objects.requireNonNull(jwkJson, "jwkJson must not be null");
        Map<String, String> members = parseTopLevelJsonMembers(jwkJson);

        String kty = members.get("kty");
        if (kty == null || kty.isBlank()) {
            throw new IllegalArgumentException("Missing required member 'kty' in JWK JSON");
        }

        return switch (kty.toUpperCase()) {
            case "RSA" -> {
                String n = members.get("n");
                String e = members.get("e");
                if (n == null || e == null) {
                    throw new IllegalArgumentException("RSA JWK must contain both 'n' and 'e' members");
                }
                yield computeRsaThumbprint(n, e);
            }
            case "EC" -> {
                String crv = members.get("crv");
                String x = members.get("x");
                String y = members.get("y");
                if (crv == null || x == null || y == null) {
                    throw new IllegalArgumentException("EC JWK must contain 'crv', 'x', and 'y' members");
                }
                yield computeEcThumbprint(crv, x, y);
            }
            case "OKP" -> {
                // RFC 8037: OKP thumbprint required members are crv, kty, x
                String crv = members.get("crv");
                String x = members.get("x");
                if (crv == null || x == null) {
                    throw new IllegalArgumentException("OKP JWK must contain 'crv' and 'x' members");
                }
                String canonical = "{\"crv\":\"" + escapeJson(crv) + "\",\"kty\":\"OKP\",\"x\":\"" + escapeJson(x) + "\"}";
                yield Base64.getUrlEncoder().withoutPadding().encodeToString(computeSha256Digest(canonical));
            }
            case "OCT" -> {
                // RFC 7638: oct thumbprint required members are k, kty
                String k = members.get("k");
                if (k == null) {
                    throw new IllegalArgumentException("oct JWK must contain 'k' member");
                }
                String canonical = "{\"k\":\"" + escapeJson(k) + "\",\"kty\":\"oct\"}";
                yield Base64.getUrlEncoder().withoutPadding().encodeToString(computeSha256Digest(canonical));
            }
            default -> throw new IllegalArgumentException("Unsupported JWK key type (kty): " + kty);
        };
    }

    /**
     * Computes the SHA-256 digest of canonical UTF-8 JSON bytes.
     *
     * @param jsonCanonical the UTF-8 canonical JSON string
     * @return 32-byte SHA-256 digest
     */
    public static byte[] computeSha256Digest(String jsonCanonical) {
        Objects.requireNonNull(jsonCanonical, "jsonCanonical must not be null");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(jsonCanonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 message digest algorithm not available in runtime", ex);
        }
    }

    /**
     * Converts a BigInteger to an unsigned big-endian byte array, stripping any leading sign-extension zero byte.
     */
    public static byte[] toUnsignedByteArray(BigInteger value) {
        Objects.requireNonNull(value, "value must not be null");
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Lightweight top-level JSON parser for JWK objects without 3rd-party dependencies.
     */
    private static Map<String, String> parseTopLevelJsonMembers(String json) {
        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON object: must start with '{' and end with '}'");
        }

        Map<String, String> map = new LinkedHashMap<>();
        int i = 1;
        int len = s.length() - 1;

        while (i < len) {
            // Skip whitespace and commas
            while (i < len && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) {
                i++;
            }
            if (i >= len) {
                break;
            }

            // Expect key string
            if (s.charAt(i) != '"') {
                throw new IllegalArgumentException("Expected string key starting with '\"' at index " + i);
            }
            int keyStart = ++i;
            StringBuilder keyBuilder = new StringBuilder();
            boolean escaped = false;
            while (i < len) {
                char c = s.charAt(i);
                if (escaped) {
                    keyBuilder.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                } else {
                    keyBuilder.append(c);
                }
                i++;
            }
            if (i >= len || s.charAt(i) != '"') {
                throw new IllegalArgumentException("Unterminated key string in JSON");
            }
            String key = keyBuilder.toString();
            i++; // skip closing quote

            // Skip whitespace to colon ':'
            while (i < len && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i >= len || s.charAt(i) != ':') {
                throw new IllegalArgumentException("Expected ':' after key \"" + key + "\" at index " + i);
            }
            i++; // skip colon

            // Skip whitespace to value
            while (i < len && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i >= len) {
                throw new IllegalArgumentException("Expected value after key \"" + key + "\"");
            }

            // Parse value
            char firstValChar = s.charAt(i);
            if (firstValChar == '"') {
                // String value
                i++;
                StringBuilder valBuilder = new StringBuilder();
                escaped = false;
                while (i < len) {
                    char c = s.charAt(i);
                    if (escaped) {
                        switch (c) {
                            case '"' -> valBuilder.append('"');
                            case '\\' -> valBuilder.append('\\');
                            case '/' -> valBuilder.append('/');
                            case 'b' -> valBuilder.append('\b');
                            case 'f' -> valBuilder.append('\f');
                            case 'n' -> valBuilder.append('\n');
                            case 'r' -> valBuilder.append('\r');
                            case 't' -> valBuilder.append('\t');
                            case 'u' -> {
                                if (i + 4 < len) {
                                    String hex = s.substring(i + 1, i + 5);
                                    valBuilder.append((char) Integer.parseInt(hex, 16));
                                    i += 4;
                                }
                            }
                            default -> valBuilder.append(c);
                        }
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        break;
                    } else {
                        valBuilder.append(c);
                    }
                    i++;
                }
                if (i >= len || s.charAt(i) != '"') {
                    throw new IllegalArgumentException("Unterminated string value for key \"" + key + "\"");
                }
                i++; // skip closing quote
                map.put(key, valBuilder.toString());
            } else if (firstValChar == '{' || firstValChar == '[') {
                // Skip nested structure
                char openChar = firstValChar;
                char closeChar = (openChar == '{') ? '}' : ']';
                int depth = 0;
                int startNested = i;
                boolean inStr = false;
                boolean esc = false;
                while (i < len) {
                    char c = s.charAt(i);
                    if (esc) {
                        esc = false;
                    } else if (c == '\\' && inStr) {
                        esc = true;
                    } else if (c == '"') {
                        inStr = !inStr;
                    } else if (!inStr) {
                        if (c == openChar) depth++;
                        else if (c == closeChar) {
                            depth--;
                            if (depth == 0) {
                                i++;
                                break;
                            }
                        }
                    }
                    i++;
                }
                map.put(key, s.substring(startNested, i));
            } else {
                // Primitive token (number, boolean, null)
                int startToken = i;
                while (i < len && s.charAt(i) != ',' && s.charAt(i) != '}' && !Character.isWhitespace(s.charAt(i))) {
                    i++;
                }
                map.put(key, s.substring(startToken, i));
            }
        }

        return map;
    }
}
