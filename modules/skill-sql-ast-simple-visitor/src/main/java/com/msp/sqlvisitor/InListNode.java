package com.msp.sqlvisitor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AST node representing an IN or NOT IN expression with a list of values.
 */
public record InListNode(SqlNode expression, List<SqlNode> values, boolean notIn) implements SqlNode {

    public InListNode {
        Objects.requireNonNull(expression, "expression must not be null");
        values = values != null ? List.copyOf(values) : Collections.emptyList();
    }

    public InListNode(SqlNode expression, List<SqlNode> values) {
        this(expression, values, false);
    }

    @Override
    public <R> R accept(SqlAstVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
