package com.msp.jsonpathfast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Lightweight, zero-dependency, high-performance JSON Path Extractor.
 * Supports deep object keys, array indices (e.g. {@code users[0].address.city}, {@code [1].name}),
 * and raw JSON subtree slicing.
 */
public final class FastJsonPathExtractor {

    private FastJsonPathExtractor() {
        // Utility class
    }

    /**
     * Extracts a string value at the specified JSON path.
     *
     * @param json the raw JSON string
     * @param path the JSON path (e.g., "users[0].name", "status")
     * @return Optional containing the string value, or empty if not found / null
     */
    public static Optional<String> extractString(String json, String path) {
        return extractNode(json, path).flatMap(node -> {
            Object v = node.value();
            if (v == null) {
                return Optional.empty();
            }
            if (v instanceof String s) {
                return Optional.of(s);
            }
            if (v instanceof Number || v instanceof Boolean) {
                return Optional.of(String.valueOf(v));
            }
            return Optional.empty();
        });
    }

    /**
     * Extracts a Long value at the specified JSON path.
     *
     * @param json the raw JSON string
     * @param path the JSON path
     * @return Optional containing the Long value, or empty
     */
    public static Optional<Long> extractLong(String json, String path) {
        return extractNode(json, path).flatMap(node -> {
            Object v = node.value();
            if (v instanceof Number n) {
                return Optional.of(n.longValue());
            }
            if (v instanceof String s) {
                try {
                    return Optional.of(Long.parseLong(s.trim()));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Extracts a Double value at the specified JSON path.
     *
     * @param json the raw JSON string
     * @param path the JSON path
     * @return Optional containing the Double value, or empty
     */
    public static Optional<Double> extractDouble(String json, String path) {
        return extractNode(json, path).flatMap(node -> {
            Object v = node.value();
            if (v instanceof Number n) {
                return Optional.of(n.doubleValue());
            }
            if (v instanceof String s) {
                try {
                    return Optional.of(Double.parseDouble(s.trim()));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Extracts a Boolean value at the specified JSON path.
     *
     * @param json the raw JSON string
     * @param path the JSON path
     * @return Optional containing the Boolean value, or empty
     */
    public static Optional<Boolean> extractBoolean(String json, String path) {
        return extractNode(json, path).flatMap(node -> {
            Object v = node.value();
            if (v instanceof Boolean b) {
                return Optional.of(b);
            }
            if (v instanceof String s) {
                String trimmed = s.trim();
                if ("true".equalsIgnoreCase(trimmed)) {
                    return Optional.of(Boolean.TRUE);
                }
                if ("false".equalsIgnoreCase(trimmed)) {
                    return Optional.of(Boolean.FALSE);
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Extracts the raw JSON slice (as substring) at the specified JSON path.
     * Suitable for retrieving nested JSON objects or arrays as valid JSON strings.
     *
     * @param json the raw JSON string
     * @param path the JSON path
     * @return Optional containing the raw JSON substring slice, or empty
     */
    public static Optional<String> extractRawJson(String json, String path) {
        if (json == null || path == null) {
            return Optional.empty();
        }
        return extractNode(json, path).map(node -> {
            int start = node.startIndex();
            int end = Math.min(node.endIndex(), json.length());
            if (start >= 0 && end >= start) {
                return json.substring(start, end).trim();
            }
            return "";
        });
    }

    // =========================================================================
    // Core Resolution
    // =========================================================================

    private static Optional<Node> extractNode(String json, String path) {
        if (json == null || path == null) {
            return Optional.empty();
        }
        String trimmedJson = json.trim();
        if (trimmedJson.isEmpty()) {
            return Optional.empty();
        }

        List<Segment> segments = parsePath(path);
        Node root;
        try {
            root = new Parser(json).parseRoot();
        } catch (Exception e) {
            return Optional.empty();
        }

        Node current = root;
        for (Segment segment : segments) {
            if (current == null) {
                return Optional.empty();
            }
            if (segment instanceof FieldSegment fs) {
                if (current.value() instanceof Map<?, ?> map) {
                    Object next = map.get(fs.name());
                    if (next instanceof Node n) {
                        current = n;
                    } else {
                        return Optional.empty();
                    }
                } else {
                    return Optional.empty();
                }
            } else if (segment instanceof IndexSegment is) {
                if (current.value() instanceof List<?> list) {
                    int idx = is.index();
                    if (idx < 0 || idx >= list.size()) {
                        return Optional.empty();
                    }
                    Object next = list.get(idx);
                    if (next instanceof Node n) {
                        current = n;
                    } else {
                        return Optional.empty();
                    }
                } else {
                    return Optional.empty();
                }
            }
        }
        return Optional.ofNullable(current);
    }

    // =========================================================================
    // Path Parser
    // =========================================================================

    sealed interface Segment permits FieldSegment, IndexSegment {}
    record FieldSegment(String name) implements Segment {}
    record IndexSegment(int index) implements Segment {}

    static List<Segment> parsePath(String path) {
        Objects.requireNonNull(path, "path cannot be null");
        String p = path.trim();
        if (p.startsWith("$.")) {
            p = p.substring(2);
        } else if (p.startsWith("$")) {
            p = p.substring(1);
        }

        List<Segment> segments = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int len = p.length();
        int i = 0;

        while (i < len) {
            char c = p.charAt(i);
            if (c == '.') {
                if (!buf.isEmpty()) {
                    segments.add(new FieldSegment(buf.toString()));
                    buf.setLength(0);
                }
                i++;
            } else if (c == '[') {
                if (!buf.isEmpty()) {
                    segments.add(new FieldSegment(buf.toString()));
                    buf.setLength(0);
                }
                int closeIdx = p.indexOf(']', i);
                if (closeIdx == -1) {
                    // Malformed index, treat remainder as field
                    buf.append(p.substring(i));
                    break;
                }
                String indexStr = p.substring(i + 1, closeIdx).trim();
                // strip quotes if key format like ['key']
                if ((indexStr.startsWith("'") && indexStr.endsWith("'")) ||
                    (indexStr.startsWith("\"") && indexStr.endsWith("\""))) {
                    segments.add(new FieldSegment(indexStr.substring(1, indexStr.length() - 1)));
                } else {
                    try {
                        int idx = Integer.parseInt(indexStr);
                        segments.add(new IndexSegment(idx));
                    } catch (NumberFormatException e) {
                        segments.add(new FieldSegment(indexStr));
                    }
                }
                i = closeIdx + 1;
            } else {
                buf.append(c);
                i++;
            }
        }

        if (!buf.isEmpty()) {
            segments.add(new FieldSegment(buf.toString()));
        }

        return segments;
    }

    // =========================================================================
    // Lightweight Recursive Descent Parser with Node Position Tracking
    // =========================================================================

    record Node(int startIndex, int endIndex, Object value) {}

    private static final class Parser {
        private final String src;
        private final int length;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
            this.length = src.length();
        }

        Node parseRoot() {
            skipWhitespace();
            Node node = parseValue();
            skipWhitespace();
            return node;
        }

        private Node parseValue() {
            skipWhitespace();
            if (pos >= length) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            int start = pos;
            char c = src.charAt(pos);

            if (c == '{') {
                return parseObject();
            } else if (c == '[') {
                return parseArray();
            } else if (c == '"') {
                String str = parseString();
                return new Node(start, pos, str);
            } else if (c == 't' || c == 'f') {
                boolean b = parseBoolean();
                return new Node(start, pos, b);
            } else if (c == 'n') {
                parseNull();
                return new Node(start, pos, null);
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                Number num = parseNumber();
                return new Node(start, pos, num);
            } else {
                throw new IllegalArgumentException("Unexpected character at " + pos + ": " + c);
            }
        }

        private Node parseObject() {
            int start = pos;
            expect('{');
            Map<String, Node> map = new LinkedHashMap<>();
            skipWhitespace();
            if (pos < length && src.charAt(pos) == '}') {
                pos++;
                return new Node(start, pos, Collections.unmodifiableMap(map));
            }

            while (pos < length) {
                skipWhitespace();
                if (pos >= length || src.charAt(pos) != '"') {
                    throw new IllegalArgumentException("Expected string key at " + pos);
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Node valNode = parseValue();
                map.put(key, valNode);
                skipWhitespace();

                if (pos < length && src.charAt(pos) == ',') {
                    pos++;
                    continue;
                } else if (pos < length && src.charAt(pos) == '}') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at " + pos);
                }
            }
            return new Node(start, pos, Collections.unmodifiableMap(map));
        }

        private Node parseArray() {
            int start = pos;
            expect('[');
            List<Node> list = new ArrayList<>();
            skipWhitespace();
            if (pos < length && src.charAt(pos) == ']') {
                pos++;
                return new Node(start, pos, Collections.unmodifiableList(list));
            }

            while (pos < length) {
                skipWhitespace();
                Node elem = parseValue();
                list.add(elem);
                skipWhitespace();

                if (pos < length && src.charAt(pos) == ',') {
                    pos++;
                    continue;
                } else if (pos < length && src.charAt(pos) == ']') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at " + pos);
                }
            }
            return new Node(start, pos, Collections.unmodifiableList(list));
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < length) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= length) {
                        throw new IllegalArgumentException("Unterminated escape sequence");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > length) {
                                throw new IllegalArgumentException("Invalid unicode escape");
                            }
                            String hex = src.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return true;
            } else if (src.startsWith("false", pos)) {
                pos += 5;
                return false;
            }
            throw new IllegalArgumentException("Invalid boolean literal at " + pos);
        }

        private void parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return;
            }
            throw new IllegalArgumentException("Invalid null literal at " + pos);
        }

        private Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
            boolean isFloating = false;
            while (pos < length) {
                char c = src.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    isFloating = true;
                    pos++;
                } else {
                    break;
                }
            }
            String numStr = src.substring(start, pos);
            if (isFloating) {
                return Double.parseDouble(numStr);
            }
            try {
                return Long.parseLong(numStr);
            } catch (NumberFormatException e) {
                return new java.math.BigInteger(numStr);
            }
        }

        private void skipWhitespace() {
            while (pos < length) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private void expect(char expected) {
            if (pos >= length || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at " + pos);
            }
            pos++;
        }
    }
}
