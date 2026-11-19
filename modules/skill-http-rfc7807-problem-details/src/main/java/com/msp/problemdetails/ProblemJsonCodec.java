package com.msp.problemdetails;

import java.net.URI;
import java.util.*;

/**
 * Pure Java JSON Serializer and Parser tailored for RFC 7807 / RFC 9457 Problem Details.
 */
public final class ProblemJsonCodec {

    private ProblemJsonCodec() {
    }

    // -------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------

    public static String serialize(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        serializeValue(map, sb);
        return sb.toString();
    }

    private static void serializeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            escapeString(s, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof URI u) {
            escapeString(u.toString(), sb);
        } else if (value instanceof InvalidParam ip) {
            sb.append("{\"name\":");
            escapeString(ip.name(), sb);
            sb.append(",\"reason\":");
            escapeString(ip.reason(), sb);
            sb.append("}");
        } else if (value instanceof Map<?, ?> m) {
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                if (entry.getValue() == null) {
                    continue; // RFC 7807/9457 recommendations: omit null members
                }
                if (!first) {
                    sb.append(",");
                }
                escapeString(String.valueOf(entry.getKey()), sb);
                sb.append(":");
                serializeValue(entry.getValue(), sb);
                first = false;
            }
            sb.append("}");
        } else if (value instanceof Iterable<?> iter) {
            sb.append("[");
            boolean first = true;
            for (Object item : iter) {
                if (!first) {
                    sb.append(",");
                }
                serializeValue(item, sb);
                first = false;
            }
            sb.append("]");
        } else if (value.getClass().isArray()) {
            sb.append("[");
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                serializeValue(java.lang.reflect.Array.get(value, i), sb);
            }
            sb.append("]");
        } else {
            escapeString(value.toString(), sb);
        }
    }

    private static void escapeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // -------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------

    public static Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON string must not be null or blank");
        }
        JsonParser parser = new JsonParser(json);
        Object root = parser.parseValue();
        if (root instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) m;
            return casted;
        }
        throw new IllegalArgumentException("Expected root JSON object, got: " + root);
    }

    private static class JsonParser {
        private final String src;
        private int pos = 0;
        private final int len;

        JsonParser(String src) {
            this.src = src;
            this.len = src.length();
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= len) {
                throw new IllegalArgumentException("Unexpected end of JSON at position " + pos);
            }
            char c = src.charAt(pos);
            if (c == '{') {
                return parseObject();
            } else if (c == '[') {
                return parseArray();
            } else if (c == '"') {
                return parseString();
            } else if (c == 't' || c == 'f') {
                return parseBoolean();
            } else if (c == 'n') {
                return parseNull();
            } else if (c == '-' || Character.isDigit(c)) {
                return parseNumber();
            }
            throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + pos);
        }

        private Map<String, Object> parseObject() {
            consume('{');
            skipWhitespace();
            Map<String, Object> map = new LinkedHashMap<>();
            if (peek() == '}') {
                consume('}');
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw new IllegalArgumentException("Expected string key in object at position " + pos);
                }
                String key = parseString();
                skipWhitespace();
                consume(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    consume(',');
                } else if (next == '}') {
                    consume('}');
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + pos + ", found '" + next + "'");
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            consume('[');
            skipWhitespace();
            List<Object> list = new ArrayList<>();
            if (peek() == ']') {
                consume(']');
                return list;
            }
            while (true) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    consume(',');
                } else if (next == ']') {
                    consume(']');
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + pos + ", found '" + next + "'");
                }
            }
            return list;
        }

        private String parseString() {
            consume('"');
            StringBuilder sb = new StringBuilder();
            while (pos < len) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= len) {
                        throw new IllegalArgumentException("Unexpected end of string escape at position " + pos);
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
                            if (pos + 4 > len) {
                                throw new IllegalArgumentException("Invalid unicode escape at position " + pos);
                            }
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new IllegalArgumentException("Unknown escape sequence '\\" + esc + "' at position " + pos);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string literal");
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            } else if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean literal at position " + pos);
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null literal at position " + pos);
        }

        private Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
            while (pos < len && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
            boolean isFloating = false;
            if (pos < len && src.charAt(pos) == '.') {
                isFloating = true;
                pos++;
                while (pos < len && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < len && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloating = true;
                pos++;
                if (pos < len && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < len && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            String numStr = src.substring(start, pos);
            if (isFloating) {
                return Double.parseDouble(numStr);
            } else {
                try {
                    return Integer.parseInt(numStr);
                } catch (NumberFormatException e) {
                    return Long.parseLong(numStr);
                }
            }
        }

        private void skipWhitespace() {
            while (pos < len) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private char peek() {
            if (pos >= len) {
                throw new IllegalArgumentException("Unexpected EOF at position " + pos);
            }
            return src.charAt(pos);
        }

        private void consume(char expected) {
            if (pos >= len || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos);
            }
            pos++;
        }
    }
}
