package com.msp.url;

import java.net.URI;

/**
 * Immutable representation of a parsed URL.
 */
public record ParsedUrl(
        String scheme,
        String userInfo,
        String host,
        int port,
        String path,
        QueryParams queryParams,
        String fragment
) {
    public ParsedUrl {
        if (queryParams == null) {
            queryParams = new QueryParams();
        }
        if (path == null) {
            path = "";
        }
    }

    /**
     * Converts to a java.net.URI.
     */
    public URI toUri() {
        return URI.create(toUrlString());
    }

    /**
     * Converts to a new UrlBuilder pre-populated with these properties.
     */
    public UrlBuilder toBuilder() {
        UrlBuilder builder = UrlBuilder.create()
                .scheme(scheme)
                .userInfo(userInfo)
                .host(host)
                .port(port)
                .path(path)
                .fragment(fragment);

        for (var entry : queryParams.toMultiValueMap().entrySet()) {
            builder.addQueryParams(entry.getKey(), entry.getValue());
        }
        return builder;
    }

    /**
     * Renders the full URL string.
     */
    public String toUrlString() {
        StringBuilder sb = new StringBuilder();
        if (scheme != null && !scheme.isBlank()) {
            sb.append(scheme).append("://");
        }
        if (userInfo != null && !userInfo.isBlank()) {
            sb.append(userInfo).append('@');
        }
        if (host != null && !host.isBlank()) {
            sb.append(host);
            if (port > 0) {
                sb.append(':').append(port);
            }
        }
        if (path != null && !path.isBlank()) {
            if (host != null && !host.isBlank() && !path.startsWith("/")) {
                sb.append('/');
            }
            sb.append(path);
        }
        if (queryParams != null && !queryParams.isEmpty()) {
            sb.append('?').append(queryParams.encode());
        }
        if (fragment != null && !fragment.isBlank()) {
            sb.append('#').append(fragment);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toUrlString();
    }
}
