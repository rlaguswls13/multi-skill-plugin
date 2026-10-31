package com.msp.paramquery;

import java.util.*;

/**
 * Immutable map representing parsed URL query parameters supporting multiple values per key.
 */
public final class QueryParameterMap {

    private final Map<String, List<String>> params;

    QueryParameterMap(Map<String, List<String>> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            this.params = Collections.emptyMap();
        } else {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            rawMap.forEach((k, v) -> {
                if (k != null && v != null) {
                    copy.put(k, Collections.unmodifiableList(new ArrayList<>(v)));
                }
            });
            this.params = Collections.unmodifiableMap(copy);
        }
    }

    /**
     * Returns the first parameter value for the specified key.
     *
     * @param key parameter name
     * @return first value, or null if key does not exist
     */
    public String get(String key) {
        List<String> values = params.get(key);
        if (values != null && !values.isEmpty()) {
            return values.getFirst();
        }
        return null;
    }

    /**
     * Returns an Optional containing the first parameter value for the key.
     *
     * @param key parameter name
     * @return Optional string value
     */
    public Optional<String> getOptional(String key) {
        return Optional.ofNullable(get(key));
    }

    /**
     * Returns all parameter values for the specified key as an unmodifiable list.
     *
     * @param key parameter name
     * @return list of values, or empty list if key not present
     */
    public List<String> getAll(String key) {
        List<String> values = params.get(key);
        return values != null ? values : List.of();
    }

    /**
     * Returns an unmodifiable multi-value map representation.
     *
     * @return map of key to value list
     */
    public Map<String, List<String>> asMap() {
        return params;
    }

    /**
     * Returns true if the map contains the specified key.
     */
    public boolean containsKey(String key) {
        return params.containsKey(key);
    }

    /**
     * Number of distinct parameter keys.
     */
    public int size() {
        return params.size();
    }

    /**
     * True if there are no query parameters.
     */
    public boolean isEmpty() {
        return params.isEmpty();
    }

    /**
     * Returns all distinct parameter keys.
     */
    public Set<String> keySet() {
        return params.keySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryParameterMap that)) return false;
        return Objects.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(params);
    }

    @Override
    public String toString() {
        return params.toString();
    }
}
