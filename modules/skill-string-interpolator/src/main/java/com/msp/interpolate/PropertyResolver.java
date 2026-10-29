package com.msp.interpolate;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Map;

/**
 * Thread-safe reflection and map-based nested property accessor.
 * Supports dot-notation paths navigating Maps, Java records, and JavaBeans.
 */
final class PropertyResolver {

    private PropertyResolver() {
        // Utility
    }

    /**
     * Resolves a key (which may be dot-delimited like "user.address.city") from context.
     */
    public static Object resolve(Object root, String path, boolean allowNested) {
        if (root == null || path == null || path.isEmpty()) {
            return null;
        }

        // Direct map check first
        if (root instanceof Map<?, ?> map) {
            if (map.containsKey(path)) {
                return map.get(path);
            }
        }

        if (!allowNested || !path.contains(".")) {
            return extractProperty(root, path);
        }

        String[] parts = path.split("\\.");
        Object current = root;

        for (String part : parts) {
            if (current == null) {
                return null;
            }
            current = extractProperty(current, part);
        }

        return current;
    }

    private static Object extractProperty(Object target, String key) {
        if (target == null || key == null) {
            return null;
        }

        // 1. Map lookup
        if (target instanceof Map<?, ?> map) {
            return map.get(key);
        }

        Class<?> clazz = target.getClass();

        // 2. Java 21 Record components
        if (clazz.isRecord()) {
            for (RecordComponent rc : clazz.getRecordComponents()) {
                if (rc.getName().equals(key)) {
                    try {
                        return rc.getAccessor().invoke(target);
                    } catch (ReflectiveOperationException ignored) {
                        return null;
                    }
                }
            }
        }

        // 3. Getter method: getXxx(), isXxx(), or xxx()
        String capitalized = Character.toUpperCase(key.charAt(0)) + (key.length() > 1 ? key.substring(1) : "");
        String[] getterNames = {"get" + capitalized, "is" + capitalized, key};

        for (String getterName : getterNames) {
            try {
                Method method = clazz.getMethod(getterName);
                if (method.getParameterCount() == 0) {
                    return method.invoke(target);
                }
            } catch (ReflectiveOperationException ignored) {
                // Continue searching next candidate
            }
        }

        // 4. Public field
        try {
            return clazz.getField(key).get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
