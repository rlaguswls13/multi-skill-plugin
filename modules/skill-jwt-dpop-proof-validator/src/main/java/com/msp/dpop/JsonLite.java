package com.msp.dpop;

import java.util.*;

/**
 * Minimalist, zero-dependency JSON parser for parsing JWT headers, payloads, and JWK maps.
 */
public final class JsonLite {

    private JsonLite() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        Object result = new Parser(json.trim()).parseValue();
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected JSON object but got: " + result);
    }

    private static final class Parser {
        private final String src;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
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
            throw new IllegalArgumentException("Unexpected character at " + pos + ": " + c);
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            consume('{');
            skipWhitespace();
            if (peek() == '}') {
                consume('}');
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                consume(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                char next = peek();
                if (next == '}') {
                    consume('}');
                    break;
                } else if (next == ',') {
                    consume(',');
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at pos " + pos);
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            consume('[');
            skipWhitespace();
            if (peek() == ']') {
                consume(']');
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char next = peek();
                if (next == ']') {
                    consume(']');
                    break;
                } else if (next == ',') {
                    consume(',');
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at pos " + pos);
                }
            }
            return list;
        }

        private String parseString() {
            consume('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                } else if (c == '\\') {
                    if (pos >= src.length()) throw new IllegalArgumentException("Unterminated escape sequence");
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
                            if (pos + 4 > src.length()) throw new IllegalArgumentException("Incomplete unicode escape");
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

        private Number parseNumber() {
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
            }
            long l = Long.parseLong(numStr);
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return (int) l;
            }
            return l;
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
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= src.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return src.charAt(pos);
        }

        private void consume(char expected) {
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at pos " + pos + " but found " + (pos < src.length() ? src.charAt(pos) : "EOF"));
            }
            pos++;
        }
    }
}
