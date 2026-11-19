package com.msp.sqlvisitor;

import java.util.Objects;

/**
 * AST node representing a column reference with optional table alias/qualifier.
 */
public record ColumnRefNode(String tableAlias, String columnName) implements SqlNode {

    public ColumnRefNode {
        Objects.requireNonNull(columnName, "columnName must not be null");
    }

    public ColumnRefNode(String columnName) {
        this(null, columnName);
    }

    public String getFullName() {
        return (tableAlias != null && !tableAlias.isBlank()) ? tableAlias + "." + columnName : columnName;
    }

    @Override
    public <R> R accept(SqlAstVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
