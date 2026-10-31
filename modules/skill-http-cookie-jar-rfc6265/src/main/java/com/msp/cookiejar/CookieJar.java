package com.msp.cookiejar;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, in-memory HTTP CookieJar complying with RFC 6265.
 */
public class CookieJar {

    private final Map<String, HttpCookieRecord> cookies = new HashMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, dd-MMM-yyyy HH:mm:ss zzz", Locale.US),
            DateTimeFormatter.ofPattern("EEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
            DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.US)
    };

    /**
     * Parses and adds a cookie from a Set-Cookie header string for the specified request URI.
     */
    public void add(URI uri, String setCookieHeader) {
        if (uri == null || setCookieHeader == null || setCookieHeader.isBlank()) {
            return;
        }

        HttpCookieRecord cookie = parseSetCookie(uri, setCookieHeader);
        if (cookie == null) {
            return;
        }

        add(cookie);
    }

    /**
     * Adds or updates a cookie in the jar.
     * If the cookie is already expired, any existing matching cookie is removed.
     */
    public void add(HttpCookieRecord cookie) {
        if (cookie == null) {
            return;
        }

        String key = buildKey(cookie.domain(), cookie.path(), cookie.name());
        rwLock.writeLock().lock();
        try {
            if (cookie.isExpired(Instant.now())) {
                cookies.remove(key);
            } else {
                cookies.put(key, cookie);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Retrieves all valid, unexpired cookies applicable for the given URI,
     * sorted according to RFC 6265 section 5.4 (longest path first, then earliest creation time).
     */
    public List<HttpCookieRecord> getCookies(URI uri) {
        if (uri == null) {
            return Collections.emptyList();
        }

        Instant now = Instant.now();
        String host = uri.getHost();
        String path = (uri.getPath() == null || uri.getPath().isBlank()) ? "/" : uri.getPath();
        boolean isSecureScheme = uri.getScheme() != null && (
                uri.getScheme().equalsIgnoreCase("https") || uri.getScheme().equalsIgnoreCase("wss")
        );

        rwLock.writeLock().lock();
        try {
            // First purge expired
            cookies.entrySet().removeIf(entry -> entry.getValue().isExpired(now));

            List<HttpCookieRecord> matched = new ArrayList<>();
            for (HttpCookieRecord cookie : cookies.values()) {
                if (cookie.matchesDomain(host) && cookie.matchesPath(path)) {
                    if (cookie.secure() && !isSecureScheme) {
                        continue;
                    }
                    matched.add(cookie);
                }
            }

            // RFC 6265 sort order:
            // Cookies with longer paths are listed before cookies with shorter paths.
            // If path length identical, older creation time first.
            matched.sort((c1, c2) -> {
                int pathCmp = Integer.compare(c2.path().length(), c1.path().length());
                if (pathCmp != 0) {
                    return pathCmp;
                }
                return c1.creationTime().compareTo(c2.creationTime());
            });

            return Collections.unmodifiableList(matched);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Generates a Cookie header value (e.g., "foo=bar; baz=qux") for the request URI.
     * Returns an empty string if no matching cookies are found.
     */
    public String getCookieHeader(URI uri) {
        List<HttpCookieRecord> matched = getCookies(uri);
        if (matched.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matched.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            HttpCookieRecord c = matched.get(i);
            sb.append(c.name()).append("=").append(c.value());
        }
        return sb.toString();
    }

    /**
     * Purges all expired cookies from the jar.
     */
    public void purgeExpired() {
        Instant now = Instant.now();
        rwLock.writeLock().lock();
        try {
            cookies.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Removes all cookies from the jar.
     */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            cookies.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Returns the number of unexpired cookies stored in the jar.
     */
    public int size() {
        purgeExpired();
        rwLock.readLock().lock();
        try {
            return cookies.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private static String buildKey(String domain, String path, String name) {
        return (domain != null ? domain.toLowerCase(Locale.ROOT) : "")
                + "\u0000" + (path != null ? path : "/")
                + "\u0000" + name;
    }

    private static HttpCookieRecord parseSetCookie(URI uri, String header) {
        String[] parts = header.split(";");
        if (parts.length == 0) {
            return null;
        }

        String nameValue = parts[0].trim();
        int eqIdx = nameValue.indexOf('=');
        if (eqIdx <= 0) {
            return null; // Empty name or missing =
        }

        String name = nameValue.substring(0, eqIdx).trim();
        String value = nameValue.substring(eqIdx + 1).trim();
        if (name.isEmpty()) {
            return null;
        }

        String domain = null;
        String path = null;
        Long maxAgeSeconds = null;
        Instant expiresAt = null;
        boolean secure = false;
        boolean httpOnly = false;
        SameSite sameSite = null;
        boolean hostOnly = true;

        for (int i = 1; i < parts.length; i++) {
            String attr = parts[i].trim();
            if (attr.isEmpty()) {
                continue;
            }

            int attrEq = attr.indexOf('=');
            String attrName = (attrEq > 0 ? attr.substring(0, attrEq) : attr).trim().toLowerCase(Locale.ROOT);
            String attrVal = (attrEq > 0 ? attr.substring(attrEq + 1) : "").trim();

            switch (attrName) {
                case "domain" -> {
                    if (!attrVal.isBlank()) {
                        domain = attrVal;
                        hostOnly = false;
                    }
                }
                case "path" -> {
                    if (!attrVal.isBlank()) {
                        path = attrVal;
                    }
                }
                case "max-age" -> {
                    try {
                        maxAgeSeconds = Long.parseLong(attrVal);
                    } catch (NumberFormatException ignored) {
                    }
                }
                case "expires" -> {
                    if (!attrVal.isBlank() && maxAgeSeconds == null) {
                        expiresAt = parseHttpDate(attrVal);
                    }
                }
                case "secure" -> secure = true;
                case "httponly" -> httpOnly = true;
                case "samesite" -> sameSite = SameSite.fromString(attrVal);
                default -> {
                    // Unknown or unhandled attribute, ignore per RFC
                }
            }
        }

        Instant creationTime = Instant.now();

        // Max-Age overrides Expires per RFC 6265 section 5.3
        if (maxAgeSeconds != null) {
            if (maxAgeSeconds <= 0) {
                expiresAt = creationTime.minusSeconds(1);
            } else {
                expiresAt = creationTime.plusSeconds(maxAgeSeconds);
            }
        }

        if (domain == null || domain.isBlank()) {
            domain = uri.getHost() != null ? uri.getHost() : "";
            hostOnly = true;
        }

        if (path == null || path.isBlank()) {
            path = computeDefaultPath(uri.getPath());
        }

        return new HttpCookieRecord(
                name,
                value,
                domain,
                path,
                expiresAt,
                secure,
                httpOnly,
                sameSite != null ? sameSite : SameSite.LAX,
                creationTime,
                hostOnly
        );
    }

    private static String computeDefaultPath(String uriPath) {
        if (uriPath == null || uriPath.isBlank() || !uriPath.startsWith("/")) {
            return "/";
        }
        int lastSlash = uriPath.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return uriPath.substring(0, lastSlash);
    }

    private static Instant parseHttpDate(String text) {
        for (DateTimeFormatter dtf : DATE_FORMATTERS) {
            try {
                return Instant.from(dtf.parse(text));
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
