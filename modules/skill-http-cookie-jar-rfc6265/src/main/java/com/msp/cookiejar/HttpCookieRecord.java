package com.msp.cookiejar;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable representation of an HTTP Cookie complying with RFC 6265.
 *
 * @param name         Cookie name (cannot be null or blank)
 * @param value        Cookie value (cannot be null)
 * @param domain       Domain attribute (lowercased)
 * @param path         Path attribute (defaults to "/")
 * @param expiresAt    Instant when cookie expires, or null if session cookie
 * @param secure       Requires HTTPS connection
 * @param httpOnly     Inaccessible to client-side scripts
 * @param sameSite     SameSite policy
 * @param creationTime Instant when cookie was received/created
 * @param hostOnly     True if Domain attribute was not explicitly specified
 */
public record HttpCookieRecord(
        String name,
        String value,
        String domain,
        String path,
        Instant expiresAt,
        boolean secure,
        boolean httpOnly,
        SameSite sameSite,
        Instant creationTime,
        boolean hostOnly
) {
    private static final Pattern IPV4_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    public HttpCookieRecord {
        Objects.requireNonNull(name, "Cookie name cannot be null");
        Objects.requireNonNull(value, "Cookie value cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Cookie name cannot be blank");
        }
        domain = domain != null ? domain.toLowerCase(Locale.ROOT) : "";
        if (domain.startsWith(".")) {
            domain = domain.substring(1);
        }
        path = (path == null || path.isBlank()) ? "/" : path;
        sameSite = sameSite != null ? sameSite : SameSite.LAX;
        creationTime = creationTime != null ? creationTime : Instant.now();
    }

    /**
     * Backward-compatible 9-parameter constructor defaulting hostOnly to false.
     */
    public HttpCookieRecord(
            String name,
            String value,
            String domain,
            String path,
            Instant expiresAt,
            boolean secure,
            boolean httpOnly,
            SameSite sameSite,
            Instant creationTime
    ) {
        this(name, value, domain, path, expiresAt, secure, httpOnly, sameSite, creationTime, false);
    }

    /**
     * Checks if the cookie is expired relative to a given instant.
     */
    public boolean isExpired(Instant now) {
        if (expiresAt == null) {
            return false;
        }
        return now.isAfter(expiresAt);
    }

    /**
     * RFC 6265 Section 5.1.3 Domain Matching algorithm.
     */
    public boolean matchesDomain(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        String lowerDomain = domain;

        if (lowerHost.equals(lowerDomain)) {
            return true;
        }
        if (hostOnly) {
            return false;
        }
        if (isIpAddress(lowerHost) || isIpAddress(lowerDomain)) {
            return false;
        }
        return lowerHost.endsWith("." + lowerDomain);
    }

    /**
     * RFC 6265 Section 5.1.4 Path Matching algorithm.
     */
    public boolean matchesPath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            requestPath = "/";
        }
        String cookiePath = path;

        // 1. Exact match
        if (requestPath.equals(cookiePath)) {
            return true;
        }

        // 2. Cookie path is prefix and ends with '/'
        if (requestPath.startsWith(cookiePath)) {
            if (cookiePath.endsWith("/")) {
                return true;
            }
            // 3. Cookie path is prefix and next char in requestPath is '/'
            if (requestPath.length() > cookiePath.length() && requestPath.charAt(cookiePath.length()) == '/') {
                return true;
            }
        }
        return false;
    }

    private static boolean isIpAddress(String host) {
        if (host.contains(":")) {
            return true; // IPv6
        }
        return IPV4_PATTERN.matcher(host).matches();
    }
}
