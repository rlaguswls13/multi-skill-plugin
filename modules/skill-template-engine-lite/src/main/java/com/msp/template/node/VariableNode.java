package com.msp.template.node;

import com.msp.template.eval.EvaluationContext;
import com.msp.template.eval.PropertyResolver;

import java.util.Objects;

/**
 * Variable interpolation node: ${expr} or ${expr:spec}.
 */
public record VariableNode(String expression, String spec) implements TemplateNode {

    public VariableNode {
        Objects.requireNonNull(expression, "expression must not be null");
    }

    @Override
    public void render(EvaluationContext ctx, StringBuilder output) {
        Object val = ctx.resolve(expression);
        String formatted = PropertyResolver.formatValue(val, spec);
        output.append(formatted);
    }
}
