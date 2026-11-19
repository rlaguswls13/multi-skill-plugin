package com.msp.sqlvisitor;

import java.util.Objects;

/**
 * AST node representing a table reference with optional schema and alias.
 */
public record TableRefNode(String schema, String tableName, String alias) implements SqlNode {

    public TableRefNode {
        Objects.requireNonNull(tableName, "tableName must not be null");
    }

    public TableRefNode(String tableName) {
        this(null, tableName, null);
    }

    public TableRefNode(String tableName, String alias) {
        this(null, tableName, alias);
    }

    public String getQualifiedName() {
        return (schema != null && !schema.isBlank()) ? schema + "." + tableName : tableName;
    }

    @Override
    public <R> R accept(SqlAstVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
