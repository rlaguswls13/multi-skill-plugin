package com.msp.url;

import java.net.URI;
import java.util.*;

/**
 * Fluent URL builder for constructing, modifying, and encoding URLs.
 */
public final class UrlBuilder {

    private String scheme;
    private String userInfo;
    private String host;
    private int port = -1;
    private final List<String> pathSegments = new ArrayList<>();
    private boolean startsWithSlash = true;
    private boolean endsWithSlash = false;
    private final QueryParams queryParams = new QueryParams();
    private String fragment;

    private UrlBuilder() {
    }

    public static UrlBuilder create() {
        return new UrlBuilder();
    }

    /**
     * Initializes a builder by parsing an existing URL string.
     */
    public static UrlBuilder from(String urlString) {
        return UrlParser.parse(urlString).toBuilder();
    }

    /**
     * Initializes a builder from a URI.
     */
    public static UrlBuilder from(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        return from(uri.toString());
    }

    public UrlBuilder scheme(String scheme) {
        this.scheme = scheme;
        return this;
    }

    public UrlBuilder http() {
        this.scheme = "http";
        return this;
    }

    public UrlBuilder https() {
        this.scheme = "https";
        return this;
    }

    public UrlBuilder userInfo(String userInfo) {
        this.userInfo = userInfo;
        return this;
    }

    public UrlBuilder userInfo(String username, String password) {
        if (username == null) {
            this.userInfo = null;
        } else if (password == null) {
            this.userInfo = username;
        } else {
            this.userInfo = username + ":" + password;
        }
        return this;
    }

    public UrlBuilder host(String host) {
        this.host = host;
        return this;
    }

    public UrlBuilder port(int port) {
        this.port = port;
        return this;
    }

    /**
     * Resets the path to the given string.
     */
    public UrlBuilder path(String path) {
        pathSegments.clear();
        if (path != null && !path.isEmpty()) {
            this.startsWithSlash = path.startsWith("/");
            this.endsWithSlash = path.length() > 1 && path.endsWith("/");
            String[] segments = path.split("/");
            for (String seg : segments) {
                if (!seg.isEmpty()) {
                    pathSegments.add(seg);
                }
            }
        }
        return this;
    }

    /**
     * Appends path segments seamlessly, managing slashes automatically.
     */
    public UrlBuilder appendPath(String... segments) {
        if (segments == null) {
            return this;
        }
        for (String segment : segments) {
            if (segment == null || segment.isEmpty()) {
                continue;
            }
            if (pathSegments.isEmpty()) {
                this.startsWithSlash = segment.startsWith("/");
            }
            this.endsWithSlash = segment.endsWith("/");
            String[] split = segment.split("/");
            for (String s : split) {
                if (!s.isEmpty()) {
                    pathSegments.add(s);
                }
            }
        }
        return this;
    }

    /**
     * Sets a query parameter, overriding existing values for this key.
     */
    public UrlBuilder queryParam(String key, String value) {
        queryParams.set(key, value);
        return this;
    }

    /**
     * Sets a query parameter using object's toString().
     */
    public UrlBuilder queryParam(String key, Object value) {
        queryParams.set(key, value != null ? value.toString() : null);
        return this;
    }

    /**
     * Bulk sets query parameters from a Map.
     */
    public UrlBuilder queryParams(Map<String, ?> params) {
        if (params != null) {
            for (Map.Entry<String, ?> entry : params.entrySet()) {
                queryParam(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    /**
     * Appends a query parameter value without replacing existing values.
     */
    public UrlBuilder addQueryParam(String key, String value) {
        queryParams.add(key, value);
        return this;
    }

    /**
     * Appends multiple values for a query parameter.
     */
    public UrlBuilder addQueryParams(String key, Collection<String> values) {
        queryParams.addAll(key, values);
        return this;
    }

    /**
     * Removes a query parameter.
     */
    public UrlBuilder removeQueryParam(String key) {
        queryParams.remove(key);
        return this;
    }

    public UrlBuilder clearQueryParams() {
        queryParams.clear();
        return this;
    }

    public UrlBuilder fragment(String fragment) {
        this.fragment = fragment;
        return this;
    }

    public UrlBuilder anchor(String anchor) {
        return fragment(anchor);
    }

    /**
     * Builds and returns the URL string.
     */
    public String build() {
        return buildParsed().toUrlString();
    }

    /**
     * Builds and returns a java.net.URI.
     */
    public URI buildUri() {
        return URI.create(build());
    }

    /**
     * Builds and returns a ParsedUrl record.
     */
    public ParsedUrl buildParsed() {
        String renderedPath = renderPath();
        return new ParsedUrl(
                scheme,
                userInfo,
                host,
                port,
                renderedPath,
                new QueryParams(queryParams.toMultiValueMap()),
                fragment
        );
    }

    private String renderPath() {
        if (pathSegments.isEmpty()) {
            return startsWithSlash ? "/" : "";
        }
        StringBuilder sb = new StringBuilder();
        if (startsWithSlash) {
            sb.append('/');
        }
        for (int i = 0; i < pathSegments.size(); i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(pathSegments.get(i));
        }
        if (endsWithSlash && sb.charAt(sb.length() - 1) != '/') {
            sb.append('/');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return build();
    }
}
