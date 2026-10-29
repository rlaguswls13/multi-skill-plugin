package com.msp.template.node;

import com.msp.template.eval.EvaluationContext;

import java.util.List;

/**
 * Sealed hierarchy of template AST nodes.
 */
public sealed interface TemplateNode permits TextNode, VariableNode, ConditionalNode, EachNode {

    /**
     * Renders this node into the output StringBuilder using the given evaluation context.
     *
     * @param ctx    the evaluation context
     * @param output the target string builder
     */
    void render(EvaluationContext ctx, StringBuilder output);
}
