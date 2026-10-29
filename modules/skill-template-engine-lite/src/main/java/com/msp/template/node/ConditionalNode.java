package com.msp.template.node;

import com.msp.template.eval.EvaluationContext;
import com.msp.template.eval.PropertyResolver;

import java.util.List;
import java.util.Objects;

/**
 * Conditional node: ${if:condition}...${else}...${endif}.
 */
public record ConditionalNode(
        String conditionExpr,
        List<TemplateNode> thenNodes,
        List<TemplateNode> elseNodes
) implements TemplateNode {

    public ConditionalNode {
        Objects.requireNonNull(conditionExpr, "conditionExpr must not be null");
        thenNodes = thenNodes != null ? List.copyOf(thenNodes) : List.of();
        elseNodes = elseNodes != null ? List.copyOf(elseNodes) : List.of();
    }

    @Override
    public void render(EvaluationContext ctx, StringBuilder output) {
        boolean invert = false;
        String expr = conditionExpr.trim();

        if (expr.startsWith("!")) {
            invert = true;
            expr = expr.substring(1).trim();
        } else if (expr.toLowerCase().startsWith("not ")) {
            invert = true;
            expr = expr.substring(4).trim();
        }

        Object val = ctx.resolve(expr);
        boolean truthy = PropertyResolver.isTruthy(val);
        if (invert) {
            truthy = !truthy;
        }

        List<TemplateNode> branch = truthy ? thenNodes : elseNodes;
        for (TemplateNode node : branch) {
            node.render(ctx, output);
        }
    }
}
