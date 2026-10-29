package com.msp.template.eval;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Hierarchical execution context for variable and expression resolution.
 */
public final class EvaluationContext {

    private final EvaluationContext parent;
    private final Object currentObject;
    private final Map<String, Object> variables;

    public EvaluationContext(Object root) {
        this(null, root, toMap(root));
    }

    public EvaluationContext(Map<String, Object> variables) {
        this(null, variables, variables != null ? new HashMap<>(variables) : new HashMap<>());
    }

    public EvaluationContext(EvaluationContext parent, Object currentObject, Map<String, Object> variables) {
        this.parent = parent;
        this.currentObject = currentObject;
        this.variables = variables != null ? new HashMap<>(variables) : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return Collections.emptyMap();
        }
        if (obj instanceof Map<?, ?> m) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return result;
        }
        return Collections.emptyMap();
    }

    /**
     * Resolves an expression (single property or dotted path).
     */
    public Object resolve(String expr) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        String path = expr.trim();

        // 1. Check direct variable in this scope
        if (variables.containsKey(path)) {
            return variables.get(path);
        }

        // Special handling for loop references
        if ("this".equals(path) || "it".equals(path)) {
            return currentObject;
        }

        // 2. Dotted path resolution: e.g. "user.profile.name"
        int firstDot = path.indexOf('.');
        if (firstDot > 0) {
            String rootName = path.substring(0, firstDot);
            String subPath = path.substring(firstDot + 1);

            Object rootVal = null;
            if (variables.containsKey(rootName)) {
                rootVal = variables.get(rootName);
            } else if (currentObject != null) {
                rootVal = PropertyResolver.resolvePath(currentObject, rootName);
            }

            if (rootVal != null) {
                return PropertyResolver.resolvePath(rootVal, subPath);
            }
        }

        // 3. Current object property lookup
        if (currentObject != null) {
            Object val = PropertyResolver.resolvePath(currentObject, path);
            if (val != null) {
                return val;
            }
        }

        // 4. Delegate to parent scope
        if (parent != null) {
            return parent.resolve(path);
        }

        return null;
    }

    /**
     * Creates a child evaluation context for nested blocks (e.g. loops).
     */
    public EvaluationContext createChild(Object item, Map<String, Object> loopVars) {
        Map<String, Object> childVars = new HashMap<>(loopVars != null ? loopVars : Collections.emptyMap());
        if (item instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    childVars.putIfAbsent(entry.getKey().toString(), entry.getValue());
                }
            }
        }
        return new EvaluationContext(this, item, childVars);
    }
}
