package com.msp.inpartition;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of SQL positional parameter (?) IN-clause partitioning.
 *
 * @param sql        the generated SQL clause (e.g. {@code col IN (?, ?)} or {@code (col IN (?) OR col IN (?))})
 * @param parameters the flattened ordered parameter list
 * @param chunkCount the number of chunks created
 */
public record SqlInClause(String sql, List<Object> parameters, int chunkCount) {

    public SqlInClause {
        Objects.requireNonNull(sql, "sql must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        parameters = Collections.unmodifiableList(List.copyOf(parameters));
    }

    /**
     * Creates an empty clause evaluating to FALSE (1 = 0).
     */
    public static SqlInClause empty() {
        return new SqlInClause("1 = 0", List.of(), 0);
    }

    /**
     * Whether the parameter list is empty.
     */
    public boolean isEmpty() {
        return parameters.isEmpty();
    }
}
