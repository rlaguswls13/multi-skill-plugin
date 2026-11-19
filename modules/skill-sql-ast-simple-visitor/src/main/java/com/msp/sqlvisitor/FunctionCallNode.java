package com.msp.sqlvisitor;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * AST node representing a SQL function call such as COUNT(*), UPPER(col), etc.
 */
public record FunctionCallNode(String functionName, List<SqlNode> arguments) implements SqlNode {

    public FunctionCallNode {
        Objects.requireNonNull(functionName, "functionName must not be null");
        functionName = functionName.trim().toUpperCase(Locale.ROOT);
        arguments = arguments != null ? List.copyOf(arguments) : Collections.emptyList();
    }

    public FunctionCallNode(String functionName) {
        this(functionName, Collections.emptyList());
    }

    @Override
    public <R> R accept(SqlAstVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
