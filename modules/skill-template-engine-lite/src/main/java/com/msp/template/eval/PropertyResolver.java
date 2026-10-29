package com.msp.template.eval;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Thread-safe utility for reflective property resolution, truthiness checking, and formatting.
 */
public final class PropertyResolver {

    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Pattern FORMAT_PATTERN = Pattern.compile(".*([#0]|yyyy|MM|dd|HH|mm|ss).*");

    private PropertyResolver() {
    }

    /**
     * Resolves a dotted property path (e.g. "user.profile.name") starting from root.
     */
    public static Object resolvePath(Object root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return root;
        }

        String[] segments = path.split("\\.");
        Object current = root;

        for (String segment : segments) {
            if (current == null) {
                return null;
            }
            current = resolveSegment(current, segment.trim());
        }

        return current;
    }

    private static Object resolveSegment(Object target, String property) {
        if (target == null || property.isEmpty()) {
            return null;
        }

        // Handle Map
        if (target instanceof Map<?, ?> map) {
            if (map.containsKey(property)) {
                return map.get(property);
            }
        }

        // Handle List or Array index
        if (property.matches("\\d+")) {
            int index = Integer.parseInt(property);
            if (target instanceof List<?> list) {
                return (index >= 0 && index < list.size()) ? list.get(index) : null;
            }
            if (target.getClass().isArray()) {
                int len = Array.getLength(target);
                return (index >= 0 && index < len) ? Array.get(target, index) : null;
            }
        }

        Class<?> clazz = target.getClass();

        // Handle Java 21 Record
        if (clazz.isRecord()) {
            for (RecordComponent rc : clazz.getRecordComponents()) {
                if (rc.getName().equals(property)) {
                    try {
                        return rc.getAccessor().invoke(target);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // Handle getter methods: getX(), isX(), or property()
        String cacheKey = clazz.getName() + "#" + property;
        Method method = METHOD_CACHE.computeIfAbsent(cacheKey, k -> findAccessorMethod(clazz, property));
        if (method != null) {
            try {
                return method.invoke(target);
            } catch (Exception ignored) {
            }
        }

        // Handle public field
        Field field = FIELD_CACHE.computeIfAbsent(cacheKey, k -> findField(clazz, property));
        if (field != null) {
            try {
                return field.get(target);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static Method findAccessorMethod(Class<?> clazz, String property) {
        String capitalized = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        String[] candidates = {"get" + capitalized, "is" + capitalized, property};

        for (String name : candidates) {
            try {
                Method m = clazz.getMethod(name);
                if (m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String property) {
        try {
            Field f = clazz.getField(property);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    /**
     * Determines truthiness of an object according to lightweight template rules.
     */
    public static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return !s.isBlank() && !s.equalsIgnoreCase("false") && !s.equals("0");
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        if (value instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (value instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        if (value instanceof Optional<?> opt) {
            return opt.isPresent();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        if (value instanceof Iterable<?> it) {
            return it.iterator().hasNext();
        }
        return true;
    }

    /**
     * Formats a value using the given specification (or falls back to default).
     * Specification syntax:
     * - "pattern" (e.g. "#,###", "yyyy-MM-dd")
     * - "pattern:fallback" (e.g. "#,###:0", "yyyy-MM-dd:N/A")
     * - "fallback" (e.g. "Guest", "Unknown")
     */
    public static String formatValue(Object value, String spec) {
        if (spec == null || spec.isBlank()) {
            return value == null ? "" : String.valueOf(value);
        }

        if (value == null) {
            // Check if there is an explicit fallback separator (or if spec is a default value)
            int colonIdx = spec.indexOf(':');
            if (colonIdx >= 0) {
                String pattern = spec.substring(0, colonIdx);
                String fallback = spec.substring(colonIdx + 1);
                if (isLikelyFormatPattern(pattern)) {
                    return fallback;
                }
            }
            return isLikelyFormatPattern(spec) ? "" : spec;
        }

        // Value is non-null: try temporal formatting first if applicable
        if (value instanceof TemporalAccessor ta) {
            try {
                return DateTimeFormatter.ofPattern(spec).withZone(ZoneId.systemDefault()).format(ta);
            } catch (Exception ignored) {
            }
        }

        // Try java.util.Date formatting
        if (value instanceof Date d) {
            try {
                return DateTimeFormatter.ofPattern(spec).withZone(ZoneId.systemDefault()).format(d.toInstant());
            } catch (Exception ignored) {
            }
        }

        // Try number formatting
        if (value instanceof Number num) {
            try {
                return new DecimalFormat(spec).format(num);
            } catch (Exception ignored) {
            }
        }

        // If spec contains a colon and formatting directly failed, try before the colon
        int colonIdx = spec.indexOf(':');
        if (colonIdx >= 0) {
            String pattern = spec.substring(0, colonIdx);
            if (value instanceof Number num) {
                try {
                    return new DecimalFormat(pattern).format(num);
                } catch (Exception ignored) {
                }
            }
            if (value instanceof TemporalAccessor ta) {
                try {
                    return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault()).format(ta);
                } catch (Exception ignored) {
                }
            }
        }

        return String.valueOf(value);
    }

    public static boolean isLikelyFormatPattern(String spec) {
        return FORMAT_PATTERN.matcher(spec).matches();
    }
}
