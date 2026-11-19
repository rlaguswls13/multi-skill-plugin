package com.msp.sqljson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable parameterized SQL snippet paired with its positional bind parameters.
 *
 * @param sql        SQL expression or predicate with {@code ?} bind placeholders
 * @param parameters ordered bind values matching each {@code ?} placeholder
 */
public record SqlFragment(String sql, List<Object> parameters) {

    public SqlFragment {
        Objects.requireNonNull(sql, "sql must not be null");
        if (sql.isBlank()) {
            throw new IllegalArgumentException("sql must not be blank");
        }
        Objects.requireNonNull(parameters, "parameters must not be null");
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    /**
     * Creates a fragment with no bind parameters.
     */
    public static SqlFragment of(String sql) {
        return new SqlFragment(sql, List.of());
    }

    /**
     * Creates a fragment with one or more bind parameters.
     */
    public static SqlFragment of(String sql, Object... params) {
        List<Object> list = new ArrayList<>(params.length);
        Collections.addAll(list, params);
        return new SqlFragment(sql, list);
    }

    /**
     * Combines this predicate with {@code other} using logical {@code AND}.
     */
    public SqlFragment and(SqlFragment other) {
        Objects.requireNonNull(other, "other fragment must not be null");
        return combine(" AND ", List.of(this, other));
    }

    /**
     * Combines this predicate with {@code other} using logical {@code OR}.
     */
    public SqlFragment or(SqlFragment other) {
        Objects.requireNonNull(other, "other fragment must not be null");
        return combine(" OR ", List.of(this, other));
    }

    /**
     * Negates this predicate using {@code NOT (...)}.
     */
    public SqlFragment not() {
        return new SqlFragment("NOT (" + sql + ")", parameters);
    }

    /**
     * Combines multiple predicates with {@code AND}.
     */
    public static SqlFragment andAll(List<SqlFragment> fragments) {
        return combine(" AND ", fragments);
    }

    /**
     * Combines multiple predicates with {@code OR}.
     */
    public static SqlFragment orAll(List<SqlFragment> fragments) {
        return combine(" OR ", fragments);
    }

    private static SqlFragment combine(String operator, List<SqlFragment> fragments) {
        Objects.requireNonNull(fragments, "fragments must not be null");
        if (fragments.isEmpty()) {
            throw new IllegalArgumentException("At least one SqlFragment is required");
        }
        if (fragments.size() == 1) {
            return Objects.requireNonNull(fragments.getFirst(), "fragment must not be null");
        }
        StringBuilder sb = new StringBuilder();
        List<Object> allParams = new ArrayList<>();
        for (int i = 0; i < fragments.size(); i++) {
            SqlFragment f = Objects.requireNonNull(fragments.get(i), "fragment must not be null");
            if (i > 0) {
                sb.append(operator);
            }
            sb.append('(').append(f.sql()).append(')');
            allParams.addAll(f.parameters());
        }
        return new SqlFragment(sb.toString(), allParams);
    }
}
