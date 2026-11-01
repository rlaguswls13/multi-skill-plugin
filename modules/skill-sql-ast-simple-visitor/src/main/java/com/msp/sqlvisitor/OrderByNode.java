package com.msp.sqlvisitor;

import java.util.Objects;

/**
 * AST node representing an ORDER BY item with expression and direction.
 */
public record OrderByNode(SqlNode expression, SortDirection direction) implements SqlNode {

    public OrderByNode {
        Objects.requireNonNull(expression, "expression must not be null");
        if (direction == null) {
            direction = SortDirection.ASC;
        }
    }

    public OrderByNode(SqlNode expression) {
        this(expression, SortDirection.ASC);
    }

    @Override
    public <R> R accept(SqlAstVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
