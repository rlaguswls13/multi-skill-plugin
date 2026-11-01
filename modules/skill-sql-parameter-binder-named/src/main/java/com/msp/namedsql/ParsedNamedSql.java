package com.msp.namedsql;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable representation of a parsed SQL statement with JDBC positional placeholders (?).
 *
 * @param preparedSql        the standard JDBC SQL with '?' placeholders
 * @param parameterOrder     the sequence of parameter names matching each '?'
 * @param parameterIndexMap  mapping from parameter name to 1-based JDBC parameter indexes
 */
public record ParsedNamedSql(
        String preparedSql,
        List<String> parameterOrder,
        Map<String, List<Integer>> parameterIndexMap
) {
    public ParsedNamedSql {
        Objects.requireNonNull(preparedSql, "preparedSql must not be null");
        Objects.requireNonNull(parameterOrder, "parameterOrder must not be null");
        Objects.requireNonNull(parameterIndexMap, "parameterIndexMap must not be null");

        parameterOrder = List.copyOf(parameterOrder);

        Map<String, List<Integer>> copyMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : parameterIndexMap.entrySet()) {
            copyMap.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        parameterIndexMap = Collections.unmodifiableMap(copyMap);
    }
}
