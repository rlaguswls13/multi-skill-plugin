package com.msp.sqlwindow;

import java.util.Objects;
import java.util.Optional;

/**
 * Formatted SQL window function expression:
 * e.g. ROW_NUMBER() OVER (PARTITION BY department_id ORDER BY salary DESC) AS dept_rank
 */
public record WindowExpression(
        WindowFunctionCall function,
        WindowSpec windowSpec,
        String alias
) {

    public WindowExpression {
        Objects.requireNonNull(function, "function cannot be null");
        Objects.requireNonNull(windowSpec, "windowSpec cannot be null");
    }

    /**
     * Set alias for this window expression (e.g. ... AS rank_col).
     */
    public WindowExpression as(String alias) {
        return new WindowExpression(function, windowSpec, alias);
    }

    public Optional<String> getAlias() {
        return Optional.ofNullable(alias);
    }

    public String toSql() {
        StringBuilder sb = new StringBuilder();
        sb.append(function.toSql()).append(" OVER ");

        if (windowSpec.namedWindow() != null && !windowSpec.namedWindow().isBlank()) {
            sb.append(windowSpec.namedWindow());
        } else {
            sb.append("(").append(windowSpec.toSql()).append(")");
        }

        if (alias != null && !alias.isBlank()) {
            sb.append(" AS ").append(alias.trim());
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toSql();
    }
}
