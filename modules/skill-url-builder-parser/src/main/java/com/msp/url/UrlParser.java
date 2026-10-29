package com.msp.url;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL parser decomposing URL strings into structured components.
 */
public final class UrlParser {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(?:([a-zA-Z][a-zA-Z0-9+.-]*):)?(?://(?:([^/@:]+(?::[^/@:]*)?)@)?([^/?#:]+)(?::(\\d+))?)?([^?#]*)(?:\\?([^#]*))?(?:#(.*))?$"
    );

    private UrlParser() {
    }

    /**
     * Parses the given URL string into a ParsedUrl.
     */
    public static ParsedUrl parse(String urlString) {
        Objects.requireNonNull(urlString, "urlString must not be null");

        // Try standard URI parsing first if standard conforming
        try {
            URI uri = new URI(urlString);
            if (uri.isAbsolute() || urlString.startsWith("//") || urlString.startsWith("/")) {
                String scheme = uri.getScheme();
                String userInfo = uri.getUserInfo();
                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath();
                String rawQuery = uri.getRawQuery();
                String fragment = uri.getFragment();

                QueryParams queryParams = (rawQuery != null) ? QueryParams.parse(rawQuery) : new QueryParams();
                return new ParsedUrl(scheme, userInfo, host, port, path, queryParams, fragment);
            }
        } catch (URISyntaxException ignored) {
            // Fall back to robust regex parsing for lenient/unencoded input
        }

        return parseRegex(urlString);
    }

    private static ParsedUrl parseRegex(String urlString) {
        Matcher matcher = URL_PATTERN.matcher(urlString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Cannot parse malformed URL: " + urlString);
        }

        String scheme = matcher.group(1);
        String userInfo = matcher.group(2);
        String host = matcher.group(3);
        String portStr = matcher.group(4);
        int port = (portStr != null && !portStr.isEmpty()) ? Integer.parseInt(portStr) : -1;
        String path = matcher.group(5);
        if (path == null) {
            path = "";
        }
        String queryStr = matcher.group(6);
        QueryParams queryParams = (queryStr != null) ? QueryParams.parse(queryStr) : new QueryParams();
        String fragment = matcher.group(7);

        return new ParsedUrl(scheme, userInfo, host, port, path, queryParams, fragment);
    }
}
