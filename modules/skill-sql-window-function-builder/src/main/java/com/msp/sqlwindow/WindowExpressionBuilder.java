package com.msp.sqlwindow;

import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for an inline window expression: function() OVER (PARTITION BY ... ORDER BY ... FRAME).
 */
public final class WindowExpressionBuilder {

    private final WindowFunctionCall function;
    private final WindowSpec.Builder specBuilder;

    public WindowExpressionBuilder(WindowFunctionCall function) {
        this.function = Objects.requireNonNull(function, "function cannot be null");
        this.specBuilder = WindowSpec.builder();
    }

    public WindowExpressionBuilder partitionBy(String... columns) {
        specBuilder.partitionBy(columns);
        return this;
    }

    public WindowExpressionBuilder partitionBy(List<String> columns) {
        specBuilder.partitionBy(columns);
        return this;
    }

    public WindowExpressionBuilder orderBy(OrderSpec... specs) {
        specBuilder.orderBy(specs);
        return this;
    }

    public WindowExpressionBuilder orderBy(String column) {
        specBuilder.orderBy(column);
        return this;
    }

    public WindowExpressionBuilder orderBy(String column, SortDirection direction) {
        specBuilder.orderBy(column, direction);
        return this;
    }

    public WindowExpressionBuilder orderBy(String column, SortDirection direction, NullsOrder nullsOrder) {
        specBuilder.orderBy(column, direction, nullsOrder);
        return this;
    }

    public WindowExpressionBuilder orderByDesc(String column) {
        specBuilder.orderByDesc(column);
        return this;
    }

    public WindowExpressionBuilder frame(FrameClause frameClause) {
        specBuilder.frame(frameClause);
        return this;
    }

    public WindowExpressionBuilder rowsBetween(FrameBound start, FrameBound end) {
        specBuilder.rowsBetween(start, end);
        return this;
    }

    public WindowExpressionBuilder rowsBetween(FrameBound start, FrameBound end, FrameExclusion exclusion) {
        specBuilder.rowsBetween(start, end, exclusion);
        return this;
    }

    public WindowExpressionBuilder rangeBetween(FrameBound start, FrameBound end) {
        specBuilder.rangeBetween(start, end);
        return this;
    }

    public WindowExpressionBuilder rangeBetween(FrameBound start, FrameBound end, FrameExclusion exclusion) {
        specBuilder.rangeBetween(start, end, exclusion);
        return this;
    }

    public WindowExpressionBuilder groupsBetween(FrameBound start, FrameBound end) {
        specBuilder.groupsBetween(start, end);
        return this;
    }

    public WindowExpressionBuilder groupsBetween(FrameBound start, FrameBound end, FrameExclusion exclusion) {
        specBuilder.groupsBetween(start, end, exclusion);
        return this;
    }

    /**
     * Builds the WindowExpression with an alias.
     */
    public WindowExpression as(String alias) {
        return new WindowExpression(function, specBuilder.build(), alias);
    }

    /**
     * Builds the WindowExpression without an alias.
     */
    public WindowExpression build() {
        return new WindowExpression(function, specBuilder.build(), null);
    }

    public String toSql() {
        return build().toSql();
    }

    @Override
    public String toString() {
        return toSql();
    }
}
