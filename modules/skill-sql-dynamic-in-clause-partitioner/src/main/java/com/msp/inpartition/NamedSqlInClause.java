package com.msp.inpartition;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Result of SQL named parameter (:name) IN-clause partitioning.
 *
 * @param sql        the generated SQL clause (e.g. {@code (col IN (:id_0_0) OR col IN (:id_1_0))})
 * @param parameters the flattened parameter map (without leading ':')
 * @param chunkCount the number of chunks created
 */
public record NamedSqlInClause(String sql, Map<String, Object> parameters, int chunkCount) {

    public NamedSqlInClause {
        Objects.requireNonNull(sql, "sql must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        parameters = Collections.unmodifiableMap(Map.copyOf(parameters));
    }

    /**
     * Creates an empty named clause evaluating to FALSE (1 = 0).
     */
    public static NamedSqlInClause empty() {
        return new NamedSqlInClause("1 = 0", Map.of(), 0);
    }

    /**
     * Whether the parameter map is empty.
     */
    public boolean isEmpty() {
        return parameters.isEmpty();
    }
}
