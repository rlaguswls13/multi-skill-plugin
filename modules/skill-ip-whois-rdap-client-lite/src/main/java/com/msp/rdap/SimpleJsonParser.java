package com.msp.rdap;

import java.util.*;

/**
 * Lightweight, zero-dependency JSON parser for pure Java 21 standard library.
 *
 * <p>Parses JSON into {@link Map}, {@link List}, {@link String}, {@link Number},
 * {@link Boolean}, or null without external dependencies.</p>
 */
public final class SimpleJsonParser {

    private final String src;
    private final int len;
    private int pos;

    private SimpleJsonParser(String src) {
        this.src = src != null ? src : "";
        this.len = this.src.length();
        this.pos = 0;
    }

    public static Object parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return new SimpleJsonParser(json.trim()).parseValue();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object val = parse(json);
        if (val instanceof Map) {
            return (Map<String, Object>) val;
        }
        return Collections.emptyMap();
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= len) {
            return null;
        }
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield parseNumber();
                }
                throw new IllegalArgumentException("Unexpected character at pos " + pos + ": " + c);
            }
        };
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        consume('{');
        skipWhitespace();
        if (pos < len && src.charAt(pos) == '}') {
            pos++;
            return map;
        }

        while (pos < len) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            consume(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            if (pos < len && src.charAt(pos) == '}') {
                pos++;
                break;
            }
            consume(',');
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        consume('[');
        skipWhitespace();
        if (pos < len && src.charAt(pos) == ']') {
            pos++;
            return list;
        }

        while (pos < len) {
            Object value = parseValue();
            list.add(value);
            skipWhitespace();
            if (pos < len && src.charAt(pos) == ']') {
                pos++;
                break;
            }
            consume(',');
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
                        if (pos + 4 > len) {
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

    private Number parseNumber() {
        int start = pos;
        if (pos < len && src.charAt(pos) == '-') {
            pos++;
        }
        while (pos < len && Character.isDigit(src.charAt(pos))) {
            pos++;
        }
        boolean isFloat = false;
        if (pos < len && src.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            while (pos < len && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        if (pos < len && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            isFloat = true;
            pos++;
            if (pos < len && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < len && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        String numStr = src.substring(start, pos);
        if (isFloat) {
            return Double.parseDouble(numStr);
        }
        try {
            return Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            return new java.math.BigInteger(numStr);
        }
    }

    private Boolean parseBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        } else if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Invalid boolean literal at pos " + pos);
    }

    private Object parseNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new IllegalArgumentException("Invalid null literal at pos " + pos);
    }

    private void skipWhitespace() {
        while (pos < len && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private void consume(char expected) {
        skipWhitespace();
        if (pos >= len || src.charAt(pos) != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' at pos " + pos +
                    " but found " + (pos < len ? "'" + src.charAt(pos) + "'" : "EOF"));
        }
        pos++;
    }
}
