package com.msp.urlcanon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance URL canonicalization utility that cleans tracking query parameters,
 * removes redundant default ports, collapses duplicate slashes, normalizes path segments,
 * and sorts query parameters alphabetically.
 */
public final class UrlCanonicalizer {

    // Regex for decomposing generic URLs: scheme, authority, path, query, fragment
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(?:([a-zA-Z][a-zA-Z0-9+.-]*):)?(?://([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#(.*))?$"
    );

    // Authority decomposition: [userinfo@]host[:port]
    private static final Pattern AUTH_PATTERN = Pattern.compile(
            "^(?:(.*?)@)?(\\[[0-9a-fA-F:]+\\]|[^:]+)(?::([0-9]+))?$"
    );

    private static final Set<String> DEFAULT_TRACKING_PARAMS = Set.of(
            "fbclid", "gclid", "gclsrc", "dclid", "_ga", "_gl",
            "_hsenc", "_hsmi", "mc_cid", "mc_eid", "yclid", "msclkid",
            "twclid", "zanpid", "wbraid", "gbraid"
    );

    private UrlCanonicalizer() {
        // Utility class
    }

    /**
     * Canonicalizes a URL string using default canonicalization options.
     *
     * @param url raw input URL (not null)
     * @return canonicalized URL
     */
    public static String canonicalize(String url) {
        return canonicalize(url, CanonicalizeOptions.DEFAULT);
    }

    /**
     * Canonicalizes a URL string using the provided configuration options.
     *
     * @param url raw input URL (not null)
     * @param options canonicalization rules
     * @return canonicalized URL
     */
    public static String canonicalize(String url, CanonicalizeOptions options) {
        Objects.requireNonNull(url, "URL cannot be null");
        Objects.requireNonNull(options, "options cannot be null");

        ParsedComponents comp = parse(url.trim());

        String scheme = comp.scheme;
        String authority = comp.authority;
        String path = comp.path;
        String query = comp.query;
        String fragment = comp.fragment;

        // 1. Lowercase scheme
        if (scheme != null && options.lowercaseSchemeAndHost()) {
            scheme = scheme.toLowerCase(Locale.ROOT);
        }

        // 2. Normalize authority (lowercase host, strip default port)
        if (authority != null) {
            authority = normalizeAuthority(authority, scheme, options);
        }

        // 3. Normalize path
        if (options.normalizePath()) {
            boolean hasAuthority = authority != null;
            path = normalizePathSegment(path, hasAuthority);
        }

        // 4. Clean tracking parameters & sort query
        if (query != null && !query.isEmpty()) {
            List<QueryParam> params = parseQueryParams(query);

            if (options.removeTrackingParams()) {
                params = params.stream()
                        .filter(p -> !isTrackingParameter(p.key(), options.extraTrackingParams()))
                        .toList();
            }

            if (options.sortQueryParams() && params.size() > 1) {
                params = new ArrayList<>(params);
                params.sort(Comparator.comparing(QueryParam::key).thenComparing(QueryParam::value));
            }

            query = formatQueryParams(params);
        }

        // 5. Fragment handling
        if (options.stripFragment()) {
            fragment = null;
        }

        return assemble(scheme, authority, path, query, fragment);
    }

    /**
     * Removes marketing and tracking parameters (e.g. utm_*, fbclid, gclid, _ga) from the URL.
     * Preserves other URL components and original query order.
     */
    public static String removeTrackingParams(String url) {
        Objects.requireNonNull(url, "URL cannot be null");
        ParsedComponents comp = parse(url.trim());
        if (comp.query == null || comp.query.isEmpty()) {
            return url;
        }

        List<QueryParam> filtered = parseQueryParams(comp.query).stream()
                .filter(p -> !isTrackingParameter(p.key(), Collections.emptySet()))
                .toList();

        String newQuery = formatQueryParams(filtered);
        return assemble(comp.scheme, comp.authority, comp.path, newQuery, comp.fragment);
    }

    /**
     * Normalizes the path component of a URL by collapsing duplicate slashes (// -> /)
     * and resolving dot segments (. and ..).
     */
    public static String normalizePath(String url) {
        Objects.requireNonNull(url, "URL cannot be null");
        ParsedComponents comp = parse(url.trim());
        String normalizedPath = normalizePathSegment(comp.path, comp.authority != null);
        return assemble(comp.scheme, comp.authority, normalizedPath, comp.query, comp.fragment);
    }

    /**
     * Sorts the query parameters of the URL alphabetically by key, then value.
     */
    public static String sortQueryParams(String url) {
        Objects.requireNonNull(url, "URL cannot be null");
        ParsedComponents comp = parse(url.trim());
        if (comp.query == null || comp.query.isEmpty()) {
            return url;
        }

        List<QueryParam> params = new ArrayList<>(parseQueryParams(comp.query));
        params.sort(Comparator.comparing(QueryParam::key).thenComparing(QueryParam::value));

        String newQuery = formatQueryParams(params);
        return assemble(comp.scheme, comp.authority, comp.path, newQuery, comp.fragment);
    }

    private static boolean isTrackingParameter(String key, Set<String> extraParams) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("utm_")) {
            return true;
        }
        if (DEFAULT_TRACKING_PARAMS.contains(lower)) {
            return true;
        }
        return extraParams.contains(lower);
    }

    private static String normalizeAuthority(String authority, String scheme, CanonicalizeOptions options) {
        Matcher m = AUTH_PATTERN.matcher(authority);
        if (!m.matches()) {
            return authority;
        }

        String userinfo = m.group(1);
        String host = m.group(2);
        String portStr = m.group(3);

        if (options.lowercaseSchemeAndHost() && host != null) {
            host = host.toLowerCase(Locale.ROOT);
        }

        if (options.stripDefaultPort() && portStr != null && scheme != null) {
            int port = Integer.parseInt(portStr);
            if (isDefaultPort(scheme, port)) {
                portStr = null;
            }
        }

        StringBuilder sb = new StringBuilder();
        if (userinfo != null) {
            sb.append(userinfo).append('@');
        }
        sb.append(host);
        if (portStr != null) {
            sb.append(':').append(portStr);
        }
        return sb.toString();
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return switch (scheme.toLowerCase(Locale.ROOT)) {
            case "http" -> port == 80;
            case "https" -> port == 443;
            case "ftp" -> port == 21;
            case "ws" -> port == 80;
            case "wss" -> port == 443;
            default -> false;
        };
    }

    private static String normalizePathSegment(String path, boolean hasAuthority) {
        if (path == null || path.isEmpty()) {
            return hasAuthority ? "/" : "";
        }

        boolean startsWithSlash = path.startsWith("/");
        boolean endsWithSlash = path.endsWith("/") && path.length() > 1;

        String[] parts = path.split("/+");
        List<String> segments = new ArrayList<>();

        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!segments.isEmpty()) {
                    segments.remove(segments.size() - 1);
                }
            } else {
                segments.add(part);
            }
        }

        StringBuilder sb = new StringBuilder();
        if (startsWithSlash || hasAuthority) {
            sb.append('/');
        }
        sb.append(String.join("/", segments));
        if (endsWithSlash && !segments.isEmpty()) {
            sb.append('/');
        }

        return sb.toString();
    }

    private static List<QueryParam> parseQueryParams(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyList();
        }
        List<QueryParam> list = new ArrayList<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                list.add(new QueryParam(pair.substring(0, eq), pair.substring(eq + 1)));
            } else {
                list.add(new QueryParam(pair, ""));
            }
        }
        return list;
    }

    private static String formatQueryParams(List<QueryParam> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append('&');
            }
            QueryParam p = params.get(i);
            sb.append(p.key());
            if (!p.value().isEmpty() || p.hasEqual()) {
                sb.append('=').append(p.value());
            }
        }
        return sb.toString();
    }

    private static ParsedComponents parse(String url) {
        Matcher m = URL_PATTERN.matcher(url);
        if (!m.matches()) {
            return new ParsedComponents(null, null, url, null, null);
        }
        return new ParsedComponents(
                m.group(1), // scheme
                m.group(2), // authority
                m.group(3), // path
                m.group(4), // query
                m.group(5)  // fragment
        );
    }

    private static String assemble(String scheme, String authority, String path, String query, String fragment) {
        StringBuilder sb = new StringBuilder();
        if (scheme != null) {
            sb.append(scheme).append(':');
        }
        if (authority != null) {
            sb.append("//").append(authority);
        }
        if (path != null) {
            sb.append(path);
        }
        if (query != null && !query.isEmpty()) {
            sb.append('?').append(query);
        }
        if (fragment != null) {
            sb.append('#').append(fragment);
        }
        return sb.toString();
    }

    private record ParsedComponents(
            String scheme,
            String authority,
            String path,
            String query,
            String fragment
    ) {
    }

    private record QueryParam(String key, String value, boolean hasEqual) {
        public QueryParam(String key, String value) {
            this(key, value, true);
        }
    }
}
