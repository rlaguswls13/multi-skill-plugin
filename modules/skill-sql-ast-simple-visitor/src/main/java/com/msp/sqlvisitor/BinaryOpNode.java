package com.msp.sqlvisitor;

import java.util.Locale;
import java.util.Objects;

/**
 * AST node representing a binary operation (e.g. left = right, left AND right).
 */
public record BinaryOpNode(SqlNode left, String operator, SqlNode right) implements SqlNode {

    public BinaryOpNode {
        Objects.requireNonNull(left, "left operand must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(right, "right operand must not be null");
        operator = operator.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isLogical() {
        return "AND".equalsIgnoreCase(operator) || "OR".equalsIgnoreCase(operator);
    }

    public boolean isComparison() {
        return switch (operator) {
            case "=", "!=", "<>", "<", ">", "<=", ">=", "LIKE", "ILIKE", "IS", "IS NOT" -> true;
            default -> false;
        };
    }

    @Override
    public <R> R accept(SqlAstVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
