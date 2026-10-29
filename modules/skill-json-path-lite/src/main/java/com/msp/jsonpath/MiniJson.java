package com.msp.jsonpath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight, zero-dependency JSON parser implementing standard JSON specification (RFC 8259).
 * Converts JSON strings into Map&lt;String, Object&gt;, List&lt;Object&gt;, and primitive wrappers.
 */
public final class MiniJson {

    private MiniJson() {
    }

    /**
     * Parses a JSON string into a Java object hierarchy.
     *
     * @param json raw JSON string
     * @return Map, List, String, Number, Boolean, or null
     * @throws IllegalArgumentException if JSON is malformed
     */
    public static Object parse(String json) {
        if (json == null) {
            return null;
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return new Parser(trimmed).parseValue();
    }

    private static final class Parser {
        private final String src;
        private final int len;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
            this.len = src.length();
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= len) {
                throw error("Unexpected end of JSON input");
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
                    throw error("Unexpected character: '" + c + "'");
                }
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }

            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("Expected string key in object");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object val = parseValue();
                map.put(key, val);

                skipWhitespace();
                char next = peek();
                if (next == '}') {
                    pos++;
                    return map;
                } else if (next == ',') {
                    pos++;
                } else {
                    throw error("Expected ',' or '}' in object");
                }
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }

            while (true) {
                Object val = parseValue();
                list.add(val);

                skipWhitespace();
                char next = peek();
                if (next == ']') {
                    pos++;
                    return list;
                } else if (next == ',') {
                    pos++;
                } else {
                    throw error("Expected ',' or ']' in array");
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < len) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                } else if (c == '\\') {
                    if (pos >= len) {
                        throw error("Unfinished escape sequence in string");
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
                                throw error("Invalid unicode escape in string");
                            }
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw error("Invalid unicode hex: " + hex);
                            }
                        }
                        default -> throw error("Invalid escape char: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("Unclosed string");
        }

        private Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') {
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
            try {
                if (isFloat) {
                    return Double.parseDouble(numStr);
                }
                long l = Long.parseLong(numStr);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                }
                return l;
            } catch (NumberFormatException e) {
                throw error("Invalid number: " + numStr);
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
            throw error("Expected boolean");
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw error("Expected null");
        }

        private void skipWhitespace() {
            while (pos < len) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private char peek() {
            skipWhitespace();
            if (pos >= len) {
                throw error("Unexpected end of JSON input");
            }
            return src.charAt(pos);
        }

        private void expect(char expected) {
            skipWhitespace();
            if (pos >= len || src.charAt(pos) != expected) {
                throw error("Expected '" + expected + "'");
            }
            pos++;
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " at position " + pos);
        }
    }
}
