package com.msp.sqlvisitor;

import java.util.Objects;

/**
 * AST node representing a JOIN clause on a table.
 */
public record JoinNode(JoinType type, TableRefNode table, SqlNode condition) implements SqlNode {

    public JoinNode {
        Objects.requireNonNull(type, "join type must not be null");
        Objects.requireNonNull(table, "joined table must not be null");
    }

    public JoinNode(JoinType type, TableRefNode table) {
        this(type, table, null);
    }

    @Override
    public <R> R accept(SqlAstVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
