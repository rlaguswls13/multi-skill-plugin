package com.msp.sqlvisitor;

/**
 * Common base interface for all SQL Abstract Syntax Tree (AST) nodes.
 */
public interface SqlNode {

    /**
     * Accepts a visitor following the Visitor design pattern.
     *
     * @param <R>     visitor return type
     * @param visitor AST visitor instance
     * @return result computed by visitor
     */
    <R> R accept(SqlAstVisitor<R> visitor);
}
