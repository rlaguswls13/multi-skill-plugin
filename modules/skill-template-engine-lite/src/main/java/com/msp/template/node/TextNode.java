package com.msp.template.node;

import com.msp.template.eval.EvaluationContext;

import java.util.Objects;

/**
 * Literal text node.
 */
public record TextNode(String text) implements TemplateNode {

    public TextNode {
        Objects.requireNonNull(text, "text must not be null");
    }

    @Override
    public void render(EvaluationContext ctx, StringBuilder output) {
        output.append(text);
    }
}
