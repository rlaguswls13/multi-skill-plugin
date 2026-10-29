package com.msp.template.node;

import com.msp.template.eval.EvaluationContext;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Loop iteration node: ${each:items}...${endeach} or ${each:items as item}...${endeach}.
 */
public record EachNode(
        String collectionExpr,
        String alias,
        List<TemplateNode> bodyNodes
) implements TemplateNode {

    public EachNode {
        Objects.requireNonNull(collectionExpr, "collectionExpr must not be null");
        bodyNodes = bodyNodes != null ? List.copyOf(bodyNodes) : List.of();
    }

    @Override
    public void render(EvaluationContext ctx, StringBuilder output) {
        Object target = ctx.resolve(collectionExpr);
        List<?> items = toList(target);
        if (items.isEmpty()) {
            return;
        }

        int total = items.size();
        for (int i = 0; i < total; i++) {
            Object item = items.get(i);
            Map<String, Object> loopVars = new HashMap<>();
            loopVars.put("@index", i);
            loopVars.put("@count", i + 1);
            loopVars.put("@first", i == 0);
            loopVars.put("@last", i == total - 1);

            if (alias != null && !alias.isBlank()) {
                loopVars.put(alias, item);
            }

            EvaluationContext childCtx = ctx.createChild(item, loopVars);
            for (TemplateNode node : bodyNodes) {
                node.render(childCtx, output);
            }
        }
    }

    private static List<?> toList(Object obj) {
        if (obj == null) {
            return Collections.emptyList();
        }
        if (obj instanceof List<?> list) {
            return list;
        }
        if (obj instanceof Collection<?> col) {
            return new ArrayList<>(col);
        }
        if (obj instanceof Iterable<?> it) {
            List<Object> list = new ArrayList<>();
            it.forEach(list::add);
            return list;
        }
        if (obj.getClass().isArray()) {
            int len = Array.getLength(obj);
            List<Object> list = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                list.add(Array.get(obj, i));
            }
            return list;
        }
        if (obj instanceof Map<?, ?> map) {
            return new ArrayList<>(map.entrySet());
        }
        return List.of(obj);
    }
}
