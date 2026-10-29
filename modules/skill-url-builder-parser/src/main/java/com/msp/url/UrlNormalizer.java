package com.msp.url;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * URL normalizer adhering to RFC 3986 normalization guidelines:
 * - Lowercases scheme and host
 * - Removes default ports (80 for http/ws, 443 for https/wss, 21 for ftp)
 * - Normalizes path dot segments (resolves /./ and /../)
 * - Removes duplicate slashes in path
 * - Trims trailing slashes (except for root "/")
 * - Unescapes unreserved percent-encoded characters
 * - Optionally canonicalizes and sorts query parameters
 */
public final class UrlNormalizer {

    private UrlNormalizer() {
    }

    /**
     * Normalizes a URL using standard normalization rules.
     */
    public static String normalize(String urlString) {
        return normalize(urlString, true);
    }

    /**
     * Normalizes a URL with optional query parameter sorting.
     */
    public static String normalize(String urlString, boolean sortQueryParams) {
        if (urlString == null || urlString.isBlank()) {
            return urlString;
        }

        ParsedUrl parsed = UrlParser.parse(urlString);

        // 1. Lowercase scheme
        String scheme = parsed.scheme() != null ? parsed.scheme().toLowerCase(Locale.ROOT) : null;

        // 2. Lowercase host
        String host = parsed.host() != null ? parsed.host().toLowerCase(Locale.ROOT) : null;

        // 3. Remove default port
        int port = parsed.port();
        if (isDefaultPort(scheme, port)) {
            port = -1;
        }

        // 4. Normalize path (resolve dot segments & duplicate slashes & trailing slash)
        String path = normalizePath(parsed.path());

        // 5. Query parameters
        QueryParams queryParams = parsed.queryParams();
        QueryParams normalizedQueryParams;
        if (sortQueryParams && !queryParams.isEmpty()) {
            normalizedQueryParams = sortAndNormalizeQueryParams(queryParams);
        } else {
            normalizedQueryParams = queryParams;
        }

        ParsedUrl normalized = new ParsedUrl(
                scheme,
                parsed.userInfo(),
                host,
                port,
                path,
                normalizedQueryParams,
                parsed.fragment()
        );

        return normalized.toUrlString();
    }

    /**
     * Normalizes path segments by resolving '.' and '..' (RFC 3986 Section 5.2.4)
     * and removing duplicate slashes.
     */
    public static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }

        boolean isAbsolute = rawPath.startsWith("/");
        String[] segments = rawPath.split("/+");
        Deque<String> stack = new ArrayDeque<>();

        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (!stack.isEmpty()) {
                    stack.removeLast();
                }
            } else {
                stack.addLast(decodeUnreserved(segment));
            }
        }

        StringBuilder sb = new StringBuilder();
        if (isAbsolute) {
            sb.append('/');
        }
        int i = 0;
        int count = stack.size();
        for (String seg : stack) {
            sb.append(seg);
            if (++i < count) {
                sb.append('/');
            }
        }

        if (sb.isEmpty()) {
            return isAbsolute ? "/" : "";
        }
        return sb.toString();
    }

    private static boolean isDefaultPort(String scheme, int port) {
        if (scheme == null || port <= 0) {
            return false;
        }
        return switch (scheme) {
            case "http", "ws" -> port == 80;
            case "https", "wss" -> port == 443;
            case "ftp" -> port == 21;
            default -> false;
        };
    }

    private static QueryParams sortAndNormalizeQueryParams(QueryParams params) {
        Map<String, List<String>> multi = params.toMultiValueMap();
        List<String> sortedKeys = new ArrayList<>(multi.keySet());
        Collections.sort(sortedKeys);

        QueryParams result = new QueryParams();
        for (String key : sortedKeys) {
            List<String> values = new ArrayList<>(multi.get(key));
            Collections.sort(values);
            for (String val : values) {
                result.add(key, val);
            }
        }
        return result;
    }

    private static String decodeUnreserved(String segment) {
        if (!segment.contains("%")) {
            return segment;
        }
        // Unreserved chars: A-Z, a-z, 0-9, -, _, ., ~
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '%' && i + 2 < segment.length()) {
                String hex = segment.substring(i + 1, i + 3);
                try {
                    int code = Integer.parseInt(hex, 16);
                    if (isUnreserved((char) code)) {
                        sb.append((char) code);
                        i += 2;
                        continue;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isUnreserved(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '-' || c == '_' || c == '.' || c == '~';
    }
}
