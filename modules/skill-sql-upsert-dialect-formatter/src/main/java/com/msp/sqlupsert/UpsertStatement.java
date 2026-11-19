package com.msp.sqlupsert;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable representation of a generated UPSERT SQL statement and its parameters.
 */
public record UpsertStatement(
    String sql,
    List<Object> parameters,
    Map<String, Object> parameterMap,
    DatabaseDialect dialect,
    String table
) {
    public UpsertStatement {
        parameters = parameters != null ? Collections.unmodifiableList(parameters) : List.of();
        parameterMap = parameterMap != null ? Collections.unmodifiableMap(parameterMap) : Map.of();
    }

    /**
     * Returns true if parameters are present.
     */
    public boolean hasParameters() {
        return !parameters.isEmpty();
    }
}
