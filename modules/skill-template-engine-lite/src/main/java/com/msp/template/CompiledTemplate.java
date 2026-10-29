package com.msp.template;

import com.msp.template.eval.EvaluationContext;
import com.msp.template.node.TemplateNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable, pre-compiled template ready for high-performance repeated rendering.
 */
public record CompiledTemplate(String rawTemplate, List<TemplateNode> nodes) {

    public CompiledTemplate {
        Objects.requireNonNull(rawTemplate, "rawTemplate must not be null");
        nodes = nodes != null ? List.copyOf(nodes) : List.of();
    }

    /**
     * Renders this template with the given map context.
     */
    public String render(Map<String, Object> context) {
        return render(new EvaluationContext(context));
    }

    /**
     * Renders this template with the given root object (Record, POJO, or Map).
     */
    public String render(Object rootObject) {
        return render(new EvaluationContext(rootObject));
    }

    /**
     * Renders this template without any context variables.
     */
    public String render() {
        return render(new EvaluationContext(null));
    }

    /**
     * Renders this template with a custom evaluation context.
     */
    public String render(EvaluationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        StringBuilder sb = new StringBuilder(rawTemplate.length() + 32);
        for (TemplateNode node : nodes) {
            node.render(context, sb);
        }
        return sb.toString();
    }
}
