package com.msp.sqlvisitor;

import java.util.Objects;

/**
 * AST node representing a literal value (String, Number, Boolean, or Null).
 */
public record LiteralNode(Object value, LiteralType type) implements SqlNode {

    public LiteralNode {
        Objects.requireNonNull(type, "type must not be null");
        if (type == LiteralType.NULL) {
            value = null;
        }
    }

    public static LiteralNode ofString(String text) {
        return new LiteralNode(text, LiteralType.STRING);
    }

    public static LiteralNode ofNumber(Number number) {
        return new LiteralNode(number, LiteralType.NUMBER);
    }

    public static LiteralNode ofBoolean(boolean flag) {
        return new LiteralNode(flag, LiteralType.BOOLEAN);
    }

    public static LiteralNode ofNull() {
        return new LiteralNode(null, LiteralType.NULL);
    }

    public String asSqlLiteral() {
        return switch (type) {
            case STRING -> "'" + (value != null ? value.toString().replace("'", "''") : "") + "'";
            case NUMBER -> value != null ? value.toString() : "0";
            case BOOLEAN -> Boolean.TRUE.equals(value) ? "TRUE" : "FALSE";
            case NULL -> "NULL";
        };
    }

    @Override
    public <R> R accept(SqlAstVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
