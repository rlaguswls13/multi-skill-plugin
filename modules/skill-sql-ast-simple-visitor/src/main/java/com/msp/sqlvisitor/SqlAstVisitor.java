package com.msp.sqlvisitor;

/**
 * Visitor interface for traversing and inspecting SQL AST nodes.
 *
 * @param <R> the return type of the visitor operations
 */
public interface SqlAstVisitor<R> {

    default R visit(SelectQueryNode node) {
        return null;
    }

    default R visit(TableRefNode node) {
        return null;
    }

    default R visit(ColumnRefNode node) {
        return null;
    }

    default R visit(BinaryOpNode node) {
        return null;
    }

    default R visit(LiteralNode node) {
        return null;
    }

    default R visit(JoinNode node) {
        return null;
    }

    default R visit(OrderByNode node) {
        return null;
    }

    default R visit(InListNode node) {
        return null;
    }

    default R visit(WildcardNode node) {
        return null;
    }

    default R visit(FunctionCallNode node) {
        return null;
    }
}
