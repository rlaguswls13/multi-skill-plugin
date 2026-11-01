package com.msp.httpauth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, RFC-compliant parser and formatter for HTTP {@code Authorization} headers.
 * <p>
 * Complies with:
 * <ul>
 *     <li>RFC 7235: Hypertext Transfer Protocol (HTTP/1.1): Authentication</li>
 *     <li>RFC 7617: The 'Basic' HTTP Authentication Scheme</li>
 *     <li>RFC 6750: The OAuth 2.0 Authorization Framework: Bearer Token Usage</li>
 * </ul>
 */
public final class HttpAuthParser {

    private static final Pattern PARAM_PATTERN = Pattern.compile("(?i)([a-z0-9_-]+)\\s*=\\s*(?:\"([^\"]*)\"|([^,\\s]+))");

    private HttpAuthParser() {
        // Utility class
    }

    /**
     * Parses an HTTP {@code Authorization} header value into an {@link AuthCredentials} instance.
     *
     * @param authorizationHeader the raw authorization header value (e.g., "Basic dXNlcjpwYXNz", "Bearer xyz")
     * @return parsed credentials representation
     * @throws IllegalArgumentException if the header is null, blank, or malformed
     */
    public static AuthCredentials parse(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IllegalArgumentException("Authorization header cannot be null or blank");
        }

        String trimmed = authorizationHeader.trim();
        int spaceIndex = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                spaceIndex = i;
                break;
            }
        }

        if (spaceIndex == -1) {
            throw new IllegalArgumentException("Invalid Authorization header format: missing credential payload after scheme");
        }

        String scheme = trimmed.substring(0, spaceIndex).trim();
        String payload = trimmed.substring(spaceIndex + 1).trim();

        if (payload.isEmpty()) {
            throw new IllegalArgumentException("Invalid Authorization header format: empty credentials payload");
        }

        if ("basic".equalsIgnoreCase(scheme)) {
            return parseBasic(payload);
        } else if ("bearer".equalsIgnoreCase(scheme)) {
            return parseBearer(payload);
        } else {
            return parseCustom(scheme, payload);
        }
    }

    /**
     * Formats username and password into an RFC 7617 Basic Authorization header value.
     *
     * @param username the user name (cannot contain a colon)
     * @param password the password
     * @return formatted Basic authorization header value (e.g. "Basic dXNlcjpwYXNz")
     * @throws NullPointerException if username or password is null
     * @throws IllegalArgumentException if username contains a colon
     */
    public static String formatBasic(String username, String password) {
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(password, "password cannot be null");

        if (username.contains(":")) {
            throw new IllegalArgumentException("RFC 7617: username cannot contain a colon (':')");
        }

        String combined = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(combined.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Formats a token into an RFC 6750 Bearer Authorization header value.
     *
     * @param token the bearer token
     * @return formatted Bearer authorization header value (e.g. "Bearer my-token")
     * @throws NullPointerException if token is null
     * @throws IllegalArgumentException if token is blank
     */
    public static String formatBearer(String token) {
        Objects.requireNonNull(token, "token cannot be null");
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Bearer token cannot be blank");
        }
        return "Bearer " + trimmed;
    }

    private static BasicAuthCredentials parseBasic(String payload) {
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            try {
                decodedBytes = Base64.getUrlDecoder().decode(payload);
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Invalid Base64 payload in Basic authentication header: " + payload, e);
            }
        }

        String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
        int colonIdx = decoded.indexOf(':');
        if (colonIdx == -1) {
            throw new IllegalArgumentException("Invalid Basic credentials: missing ':' user-password delimiter");
        }

        String username = decoded.substring(0, colonIdx);
        String password = decoded.substring(colonIdx + 1);
        return new BasicAuthCredentials(username, password);
    }

    private static BearerAuthCredentials parseBearer(String payload) {
        return new BearerAuthCredentials(payload);
    }

    private static CustomAuthCredentials parseCustom(String scheme, String payload) {
        Map<String, String> parameters = new LinkedHashMap<>();
        Matcher matcher = PARAM_PATTERN.matcher(payload);
        while (matcher.find()) {
            String key = matcher.group(1);
            String quotedVal = matcher.group(2);
            String unquotedVal = matcher.group(3);
            String value = quotedVal != null ? quotedVal : (unquotedVal != null ? unquotedVal : "");
            parameters.put(key, value);
        }
        return new CustomAuthCredentials(scheme, payload, Collections.unmodifiableMap(parameters));
    }
}
