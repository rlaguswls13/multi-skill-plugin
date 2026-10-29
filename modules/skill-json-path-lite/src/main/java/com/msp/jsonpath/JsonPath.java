package com.msp.jsonpath;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.*;

/**
 * Pure Java 21, zero-dependency JSONPath parser and evaluator supporting
 * dot-notation, bracket notation, array indexing, and length inspection.
 * Works seamlessly on {@link Map}, {@link List}, Java Records, POJOs, or raw JSON strings.
 */
public final class JsonPath {

    private JsonPath() {
    }

    /**
     * Evaluates a path on a Java object (Map, List, Record, etc.).
     *
     * @param root root object
     * @param path path expression (e.g. "user.orders[0].items[1].name", "items.length")
     * @return Optional containing found value or empty if not found
     */
    public static Optional<Object> find(Object root, String path) {
        if (root == null) {
            return Optional.empty();
        }
        if (path == null || path.isBlank() || "$".equals(path.trim())) {
            return Optional.of(root);
        }

        List<PathSegment> segments = parsePath(path);
        Object current = root;

        for (PathSegment segment : segments) {
            if (current == null) {
                return Optional.empty();
            }

            if (segment instanceof PathSegment.Index idx) {
                current = extractByIndex(current, idx.index());
                if (current == null) {
                    return Optional.empty();
                }
            } else if (segment instanceof PathSegment.Property prop) {
                String name = prop.name();
                if ("length".equals(name) || "size".equals(name)) {
                    Integer len = extractLength(current);
                    if (len != null) {
                        current = len;
                        continue;
                    }
                }

                current = extractProperty(current, name);
                if (current == null) {
                    return Optional.empty();
                }
            }
        }

        return Optional.ofNullable(current);
    }

    /**
     * Evaluates a path on a raw JSON string.
     *
     * @param json raw JSON text
     * @param path path expression
     * @return Optional containing found value or empty
     */
    public static Optional<Object> find(String json, String path) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            Object root = MiniJson.parse(json);
            return find(root, path);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Checks if the given path exists and is not null.
     */
    public static boolean exists(Object root, String path) {
        return find(root, path).isPresent();
    }

    public static boolean exists(String json, String path) {
        return find(json, path).isPresent();
    }

    // --- Typed Accessors ---

    public static Optional<String> getString(Object root, String path) {
        return find(root, path).map(Object::toString);
    }

    public static Optional<String> getString(String json, String path) {
        return find(json, path).map(Object::toString);
    }

    public static Optional<Integer> getInt(Object root, String path) {
        return find(root, path).map(val -> {
            if (val instanceof Number n) {
                return n.intValue();
            }
            try {
                return Integer.parseInt(val.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    public static Optional<Integer> getInt(String json, String path) {
        return find(json, path).flatMap(val -> getInt(val, "$"));
    }

    public static Optional<Long> getLong(Object root, String path) {
        return find(root, path).map(val -> {
            if (val instanceof Number n) {
                return n.longValue();
            }
            try {
                return Long.parseLong(val.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    public static Optional<Long> getLong(String json, String path) {
        return find(json, path).flatMap(val -> getLong(val, "$"));
    }

    public static Optional<Double> getDouble(Object root, String path) {
        return find(root, path).map(val -> {
            if (val instanceof Number n) {
                return n.doubleValue();
            }
            try {
                return Double.parseDouble(val.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    public static Optional<Double> getDouble(String json, String path) {
        return find(json, path).flatMap(val -> getDouble(val, "$"));
    }

    public static Optional<Boolean> getBool(Object root, String path) {
        return find(root, path).map(val -> {
            if (val instanceof Boolean b) {
                return b;
            }
            if (val instanceof Number n) {
                return n.intValue() != 0;
            }
            String s = val.toString().trim().toLowerCase();
            return "true".equals(s) || "1".equals(s);
        });
    }

    public static Optional<Boolean> getBool(String json, String path) {
        return find(json, path).flatMap(val -> getBool(val, "$"));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getList(Object root, String path) {
        return find(root, path)
                .filter(val -> val instanceof List)
                .map(val -> (List<Object>) val)
                .orElse(Collections.emptyList());
    }

    public static List<Object> getList(String json, String path) {
        return find(json, path)
                .filter(val -> val instanceof List)
                .map(val -> {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) val;
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    public static Optional<Map<String, Object>> getMap(Object root, String path) {
        return find(root, path)
                .filter(val -> val instanceof Map)
                .map(val -> (Map<String, Object>) val);
    }

    public static Optional<Map<String, Object>> getMap(String json, String path) {
        return find(json, path)
                .filter(val -> val instanceof Map)
                .map(val -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) val;
                    return map;
                });
    }

    // --- Path Parsing ---

    static List<PathSegment> parsePath(String rawPath) {
        String p = rawPath.trim();
        if (p.startsWith("$.")) {
            p = p.substring(2);
        } else if (p.startsWith("$")) {
            p = p.substring(1);
        }

        List<PathSegment> segments = new ArrayList<>();
        int len = p.length();
        int i = 0;
        StringBuilder currentProp = new StringBuilder();

        while (i < len) {
            char c = p.charAt(i);

            if (c == '.') {
                if (!currentProp.isEmpty()) {
                    segments.add(new PathSegment.Property(currentProp.toString()));
                    currentProp.setLength(0);
                }
                i++;
            } else if (c == '[') {
                if (!currentProp.isEmpty()) {
                    segments.add(new PathSegment.Property(currentProp.toString()));
                    currentProp.setLength(0);
                }
                i++; // skip '['
                // skip whitespace
                while (i < len && Character.isWhitespace(p.charAt(i))) {
                    i++;
                }
                if (i >= len) {
                    throw new IllegalArgumentException("Unclosed bracket in path: " + rawPath);
                }

                char bracketChar = p.charAt(i);
                if (bracketChar == '\'' || bracketChar == '"') {
                    // quoted property key: ['foo'] or ["bar"]
                    char quote = bracketChar;
                    i++; // skip quote
                    StringBuilder key = new StringBuilder();
                    while (i < len && p.charAt(i) != quote) {
                        key.append(p.charAt(i++));
                    }
                    if (i >= len) {
                        throw new IllegalArgumentException("Unclosed quote in bracket: " + rawPath);
                    }
                    i++; // skip quote
                    while (i < len && p.charAt(i) != ']') {
                        i++;
                    }
                    i++; // skip ']'
                    segments.add(new PathSegment.Property(key.toString()));
                } else {
                    // integer index: [0] or [-1]
                    StringBuilder num = new StringBuilder();
                    while (i < len && p.charAt(i) != ']') {
                        num.append(p.charAt(i++));
                    }
                    if (i >= len) {
                        throw new IllegalArgumentException("Unclosed bracket in path: " + rawPath);
                    }
                    i++; // skip ']'
                    try {
                        int index = Integer.parseInt(num.toString().trim());
                        segments.add(new PathSegment.Index(index));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid array index [" + num + "] in path: " + rawPath);
                    }
                }
            } else {
                currentProp.append(c);
                i++;
            }
        }

        if (!currentProp.isEmpty()) {
            segments.add(new PathSegment.Property(currentProp.toString()));
        }

        return segments;
    }

    private static Object extractByIndex(Object target, int index) {
        if (target instanceof List<?> list) {
            int actualIdx = index < 0 ? list.size() + index : index;
            if (actualIdx >= 0 && actualIdx < list.size()) {
                return list.get(actualIdx);
            }
            return null;
        }
        if (target.getClass().isArray()) {
            int len = Array.getLength(target);
            int actualIdx = index < 0 ? len + index : index;
            if (actualIdx >= 0 && actualIdx < len) {
                return Array.get(target, actualIdx);
            }
            return null;
        }
        return null;
    }

    private static Object extractProperty(Object target, String name) {
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }

        // Java Record accessor support
        if (target.getClass().isRecord()) {
            for (RecordComponent rc : target.getClass().getRecordComponents()) {
                if (rc.getName().equals(name)) {
                    try {
                        return rc.getAccessor().invoke(target);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // Getter or Bean accessor support
        try {
            String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            for (String prefix : List.of("get", "is")) {
                try {
                    Method m = target.getClass().getMethod(prefix + capitalized);
                    return m.invoke(target);
                } catch (NoSuchMethodException ignored) {
                }
            }
            // Method with exact name
            try {
                Method m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static Integer extractLength(Object target) {
        if (target instanceof Collection<?> c) {
            return c.size();
        }
        if (target instanceof Map<?, ?> m) {
            return m.size();
        }
        if (target.getClass().isArray()) {
            return Array.getLength(target);
        }
        if (target instanceof CharSequence cs) {
            return cs.length();
        }
        return null;
    }
}
