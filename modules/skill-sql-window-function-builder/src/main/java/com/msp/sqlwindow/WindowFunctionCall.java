package com.msp.sqlwindow;

import java.util.List;
import java.util.Objects;

/**
 * Representation of a window function invocation (e.g. ROW_NUMBER(), SUM(col), LEAD(col, 1)).
 */
public record WindowFunctionCall(String name, List<String> arguments) {

    public WindowFunctionCall {
        Objects.requireNonNull(name, "function name cannot be null");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public static WindowFunctionCall of(String name, String... args) {
        return new WindowFunctionCall(name, args != null ? List.of(args) : List.of());
    }

    public static WindowFunctionCall of(String name, List<String> args) {
        return new WindowFunctionCall(name, args);
    }

    public String toSql() {
        return name + "(" + String.join(", ", arguments) + ")";
    }

    /**
     * Start building an inline window specification: function() OVER (...)
     */
    public WindowExpressionBuilder over() {
        return new WindowExpressionBuilder(this);
    }

    /**
     * Attach a pre-built window specification.
     */
    public WindowExpression over(WindowSpec windowSpec) {
        return new WindowExpression(this, windowSpec, null);
    }

    /**
     * Attach a named window reference: function() OVER (window_name) or OVER window_name.
     */
    public WindowExpression over(String namedWindow) {
        return new WindowExpression(this, WindowSpec.named(namedWindow), null);
    }

    @Override
    public String toString() {
        return toSql();
    }
}
