package com.msp.sqlvisitor;

/**
 * AST node representing a wildcard projection (e.g., '*' or 'alias.*').
 */
public record WildcardNode(String tableAlias) implements SqlNode {

    public WildcardNode() {
        this(null);
    }

    public boolean isQualified() {
        return tableAlias != null && !tableAlias.isBlank();
    }

    public String getExpression() {
        return isQualified() ? tableAlias + ".*" : "*";
    }

    @Override
    public <R> R accept(SqlAstVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
