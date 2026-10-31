package com.msp.paramquery;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Bidirectional URL query string builder and parser adhering strictly to RFC 3986 UTF-8 encoding.
 * Supports multi-value parameters, chained fluent API, and seamless parsing from full URLs.
 */
public class QueryParameterBuilder {

    private final Map<String, List<String>> params = new LinkedHashMap<>();

    public QueryParameterBuilder() {
    }

    /**
     * Creates a new builder initialized with existing parameters from a query string or URL.
     *
     * @param queryString raw query string (e.g. "?a=1&b=2") or full URL
     * @return initialized builder
     */
    public static QueryParameterBuilder from(String queryString) {
        QueryParameterBuilder builder = new QueryParameterBuilder();
        QueryParameterMap parsed = parse(queryString);
        parsed.asMap().forEach(builder::add);
        return builder;
    }

    /**
     * Adds a single key-value parameter. If value is null, it is treated as an empty string.
     *
     * @param key   parameter name
     * @param value parameter value
     * @return this builder for method chaining
     */
    public QueryParameterBuilder add(String key, Object value) {
        Objects.requireNonNull(key, "parameter key must not be null");
        params.computeIfAbsent(key, k -> new ArrayList<>())
                .add(value != null ? value.toString() : "");
        return this;
    }

    /**
     * Adds multiple values for the given parameter key.
     *
     * @param key    parameter name
     * @param values collection of values
     * @return this builder for method chaining
     */
    public QueryParameterBuilder add(String key, List<?> values) {
        Objects.requireNonNull(key, "parameter key must not be null");
        if (values != null && !values.isEmpty()) {
            List<String> list = params.computeIfAbsent(key, k -> new ArrayList<>());
            for (Object v : values) {
                list.add(v != null ? v.toString() : "");
            }
        }
        return this;
    }

    /**
     * Adds all entries from a given map. If a value is a Collection or Iterable, each element is added.
     *
     * @param map map of parameters
     * @return this builder for method chaining
     */
    public QueryParameterBuilder addAll(Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return this;
        }
        map.forEach((k, v) -> {
            if (k != null) {
                if (v instanceof Collection<?> col) {
                    add(k, new ArrayList<>(col));
                } else if (v != null && v.getClass().isArray()) {
                    add(k, Arrays.asList((Object[]) v));
                } else {
                    add(k, v);
                }
            }
        });
        return this;
    }

    /**
     * Sets a parameter, replacing any existing values for the given key.
     *
     * @param key   parameter name
     * @param value parameter value
     * @return this builder
     */
    public QueryParameterBuilder set(String key, Object value) {
        Objects.requireNonNull(key, "parameter key must not be null");
        List<String> list = new ArrayList<>();
        list.add(value != null ? value.toString() : "");
        params.put(key, list);
        return this;
    }

    /**
     * Removes the specified parameter key and all its associated values.
     *
     * @param key parameter name
     * @return this builder
     */
    public QueryParameterBuilder remove(String key) {
        if (key != null) {
            params.remove(key);
        }
        return this;
    }

    /**
     * Clears all parameters.
     */
    public QueryParameterBuilder clear() {
        params.clear();
        return this;
    }

    /**
     * Builds the encoded query string prefixed with '?' (e.g. "?name=John&tags=A&tags=B").
     * Returns an empty string if there are no parameters.
     *
     * @return encoded query string with leading '?' or ""
     */
    public String build() {
        String query = buildWithoutQuestionMark();
        return query.isEmpty() ? "" : "?" + query;
    }

    /**
     * Builds the encoded query string without leading '?' (e.g. "name=John&tags=A&tags=B").
     * Returns an empty string if there are no parameters.
     *
     * @return encoded query string or ""
     */
    public String buildWithoutQuestionMark() {
        if (params.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String encodedKey = encodeRfc3986(entry.getKey());
            for (String val : entry.getValue()) {
                if (!first) {
                    sb.append('&');
                } else {
                    first = false;
                }
                sb.append(encodedKey);
                if (val != null) {
                    sb.append('=').append(encodeRfc3986(val));
                }
            }
        }

        return sb.toString();
    }

    /**
     * Parses a raw query string or full URL into an immutable {@link QueryParameterMap}.
     *
     * @param rawQueryOrUrl raw query string (e.g. "?a=1&b=2") or URL
     * @return parsed immutable QueryParameterMap
     */
    public static QueryParameterMap parse(String rawQueryOrUrl) {
        if (rawQueryOrUrl == null || rawQueryOrUrl.isBlank()) {
            return new QueryParameterMap(Collections.emptyMap());
        }

        String input = rawQueryOrUrl.trim();

        // Strip fragment (#...)
        int hashIdx = input.indexOf('#');
        if (hashIdx >= 0) {
            input = input.substring(0, hashIdx);
        }

        // Extract query portion after '?'
        int queryIdx = input.indexOf('?');
        if (queryIdx >= 0) {
            input = input.substring(queryIdx + 1);
        }

        if (input.isEmpty()) {
            return new QueryParameterMap(Collections.emptyMap());
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        String[] pairs = input.split("&");

        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int eqIdx = pair.indexOf('=');
            String key;
            String val;
            if (eqIdx >= 0) {
                key = decode(pair.substring(0, eqIdx));
                val = decode(pair.substring(eqIdx + 1));
            } else {
                key = decode(pair);
                val = "";
            }

            result.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
        }

        return new QueryParameterMap(result);
    }

    private static String encodeRfc3986(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%7E", "~");
    }

    private static String decode(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return build();
    }
}
