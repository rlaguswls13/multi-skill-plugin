package com.msp.url;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Multi-map representation of URL query parameters, preserving insertion order.
 * Supports multiple values per key (e.g. {@code tag=java&tag=spring}).
 */
public final class QueryParams {

    private final Map<String, List<String>> params;

    public QueryParams() {
        this.params = new LinkedHashMap<>();
    }

    public QueryParams(Map<String, List<String>> initialParams) {
        this.params = new LinkedHashMap<>();
        if (initialParams != null) {
            for (Map.Entry<String, List<String>> entry : initialParams.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    this.params.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
            }
        }
    }

    /**
     * Sets a query parameter, replacing any existing values for this key.
     */
    public QueryParams set(String key, String value) {
        Objects.requireNonNull(key, "key must not be null");
        List<String> list = new ArrayList<>();
        if (value != null) {
            list.add(value);
        }
        params.put(key, list);
        return this;
    }

    /**
     * Adds a value to a query parameter without replacing existing values.
     */
    public QueryParams add(String key, String value) {
        Objects.requireNonNull(key, "key must not be null");
        if (value != null) {
            params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return this;
    }

    /**
     * Adds multiple values for a query parameter.
     */
    public QueryParams addAll(String key, Collection<String> values) {
        Objects.requireNonNull(key, "key must not be null");
        if (values != null) {
            List<String> list = params.computeIfAbsent(key, k -> new ArrayList<>());
            for (String val : values) {
                if (val != null) {
                    list.add(val);
                }
            }
        }
        return this;
    }

    /**
     * Gets the first value associated with the key.
     */
    public Optional<String> getFirst(String key) {
        List<String> values = params.get(key);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.getFirst());
    }

    /**
     * Gets all values associated with the key. Returns an unmodifiable list.
     */
    public List<String> getAll(String key) {
        List<String> values = params.get(key);
        if (values == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * Checks if the key exists.
     */
    public boolean containsKey(String key) {
        return params.containsKey(key);
    }

    /**
     * Removes the key and all associated values.
     */
    public QueryParams remove(String key) {
        params.remove(key);
        return this;
    }

    /**
     * Clears all parameters.
     */
    public void clear() {
        params.clear();
    }

    public boolean isEmpty() {
        return params.isEmpty();
    }

    public int size() {
        return params.size();
    }

    /**
     * Returns a copy of the multi-value map.
     */
    public Map<String, List<String>> toMultiValueMap() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Returns a map containing only the first value for each key.
     */
    public Map<String, String> toSingleValueMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                map.put(entry.getKey(), entry.getValue().getFirst());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Encodes query parameters into a URL-encoded string using UTF-8.
     * Example: {@code k1=v1&k1=v2&k2=v3}
     */
    public String encode() {
        return encode(StandardCharsets.UTF_8);
    }

    /**
     * Encodes query parameters into a URL-encoded string using the specified charset.
     */
    public String encode(Charset charset) {
        if (params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String encodedKey = URLEncoder.encode(entry.getKey(), charset);
            List<String> values = entry.getValue();
            if (values.isEmpty()) {
                if (!first) {
                    sb.append('&');
                }
                sb.append(encodedKey);
                first = false;
            } else {
                for (String val : values) {
                    if (!first) {
                        sb.append('&');
                    }
                    sb.append(encodedKey);
                    if (val != null) {
                        sb.append('=').append(URLEncoder.encode(val, charset));
                    }
                    first = false;
                }
            }
        }
        return sb.toString();
    }

    /**
     * Parses a query string into a QueryParams instance using UTF-8.
     * Leading '?' is automatically stripped if present.
     */
    public static QueryParams parse(String queryString) {
        return parse(queryString, StandardCharsets.UTF_8);
    }

    /**
     * Parses a query string into a QueryParams instance using the specified charset.
     */
    public static QueryParams parse(String queryString, Charset charset) {
        QueryParams result = new QueryParams();
        if (queryString == null || queryString.isBlank()) {
            return result;
        }

        String raw = queryString.startsWith("?") ? queryString.substring(1) : queryString;
        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx == -1) {
                String key = URLDecoder.decode(pair, charset);
                result.add(key, "");
            } else {
                String key = URLDecoder.decode(pair.substring(0, idx), charset);
                String value = URLDecoder.decode(pair.substring(idx + 1), charset);
                result.add(key, value);
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryParams that)) return false;
        return Objects.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(params);
    }

    @Override
    public String toString() {
        return encode();
    }
}
