package com.msp.jwtextract;

import java.util.*;

/**
 * Lightweight, zero-dependency JSON parser for parsing JWT headers and payloads.
 */
public final class SimpleJsonParser {

    private final String src;
    private int pos = 0;

    private SimpleJsonParser(String src) {
        this.src = src != null ? src : "";
    }

    /**
     * Parses the given JSON string into a Java Map.
     *
     * @param json JSON object string
     * @return parsed key-value map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        SimpleJsonParser parser = new SimpleJsonParser(json);
        Object result = parser.parseValue();
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected JSON object, but found: " + result);
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= src.length()) {
            return null;
        }

        char c = src.charAt(pos);
        if (c == '{') {
            return parseObjectInternal();
        } else if (c == '[') {
            return parseArrayInternal();
        } else if (c == '"' || c == '\'') {
            return parseStringInternal();
        } else if (c == 't' || c == 'f') {
            return parseBooleanInternal();
        } else if (c == 'n') {
            return parseNullInternal();
        } else if (c == '-' || Character.isDigit(c)) {
            return parseNumberInternal();
        } else {
            throw new IllegalArgumentException("Unexpected character at pos " + pos + ": " + c);
        }
    }

    private Map<String, Object> parseObjectInternal() {
        match('{');
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
            match(':');
            Object value = parseValue();
            map.put(key, value);

            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                skipWhitespace();
            } else if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                break;
            } else {
                throw new IllegalArgumentException("Expected ',' or '}' at pos " + pos);
            }
        }
        return map;
    }

    private List<Object> parseArrayInternal() {
        match('[');
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
                skipWhitespace();
            } else if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                break;
            } else {
                throw new IllegalArgumentException("Expected ',' or ']' at pos " + pos);
            }
        }
        return list;
    }

    private String parseStringInternal() {
        char quote = src.charAt(pos);
        if (quote != '"' && quote != '\'') {
            throw new IllegalArgumentException("Expected quote at pos " + pos);
        }
        pos++;
        StringBuilder sb = new StringBuilder();

        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == quote) {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("Unterminated escape sequence");
                }
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\'' -> sb.append('\'');
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
                        pos += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated string literal");
    }

    private Boolean parseBooleanInternal() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        } else if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Invalid boolean literal at pos " + pos);
    }

    private Object parseNullInternal() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new IllegalArgumentException("Invalid null literal at pos " + pos);
    }

    private Number parseNumberInternal() {
        int start = pos;
        if (src.charAt(pos) == '-') {
            pos++;
        }
        boolean isDouble = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isDigit(c)) {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                isDouble = true;
                pos++;
            } else {
                break;
            }
        }

        String numStr = src.substring(start, pos);
        if (isDouble) {
            return Double.parseDouble(numStr);
        } else {
            return Long.parseLong(numStr);
        }
    }

    private void match(char expected) {
        skipWhitespace();
        if (pos >= src.length() || src.charAt(pos) != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' at pos " + pos);
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                pos++;
            } else {
                break;
            }
        }
    }
}
