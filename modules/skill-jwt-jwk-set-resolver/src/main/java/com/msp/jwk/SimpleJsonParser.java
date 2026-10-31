package com.msp.jwk;

import java.util.*;

/**
 * Minimal zero-dependency JSON parser strictly for parsing JWKS JSON structures.
 * Supports objects, arrays, strings, numbers, booleans, and null.
 */
final class SimpleJsonParser {

    private final String src;
    private int pos = 0;

    private SimpleJsonParser(String src) {
        this.src = Objects.requireNonNull(src, "JSON string must not be null");
    }

    public static Object parse(String json) {
        return new SimpleJsonParser(json).parseValue();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object val = parse(json);
        if (val instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected JSON object, got: " + (val == null ? "null" : val.getClass().getSimpleName()));
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= src.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON input");
        }
        char c = src.charAt(pos);
        if (c == '{') {
            return parseObjectInternal();
        } else if (c == '[') {
            return parseArrayInternal();
        } else if (c == '"') {
            return parseStringInternal();
        } else if (c == 't' || c == 'f') {
            return parseBooleanInternal();
        } else if (c == 'n') {
            return parseNullInternal();
        } else if (c == '-' || Character.isDigit(c)) {
            return parseNumberInternal();
        }
        throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + pos);
    }

    private Map<String, Object> parseObjectInternal() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == '}') {
            pos++;
            return map;
        }

        while (pos < src.length()) {
            skipWhitespace();
            String key = parseStringInternal();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);

            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
            } else if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            } else {
                throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
            }
        }
        throw new IllegalArgumentException("Unterminated JSON object");
    }

    private List<Object> parseArrayInternal() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ']') {
            pos++;
            return list;
        }

        while (pos < src.length()) {
            Object value = parseValue();
            list.add(value);
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
            } else if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            } else {
                throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
            }
        }
        throw new IllegalArgumentException("Unterminated JSON array");
    }

    private String parseStringInternal() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("Invalid escape sequence at end of string");
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
                        if (pos + 4 > src.length()) {
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
        throw new IllegalArgumentException("Unterminated JSON string");
    }

    private Boolean parseBooleanInternal() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        } else if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Invalid boolean literal at position " + pos);
    }

    private Object parseNullInternal() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new IllegalArgumentException("Invalid null literal at position " + pos);
    }

    private Number parseNumberInternal() {
        int start = pos;
        if (src.charAt(pos) == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
            pos++;
        }
        boolean isFloating = false;
        if (pos < src.length() && src.charAt(pos) == '.') {
            isFloating = true;
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            isFloating = true;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        String numStr = src.substring(start, pos);
        if (isFloating) {
            return Double.parseDouble(numStr);
        } else {
            long l = Long.parseLong(numStr);
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return (int) l;
            }
            return l;
        }
    }

    private void expect(char expected) {
        skipWhitespace();
        if (pos >= src.length() || src.charAt(pos) != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos);
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }
}
