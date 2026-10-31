package com.msp.jsoncanonical;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * High-performance, zero-dependency RFC 8785 JSON Canonicalization Scheme (JCS) processor.
 * <p>
 * Performs:
 * <ul>
 *     <li>Lexicographical sorting of object keys by UTF-16 code units (RFC 8785)</li>
 *     <li>Whitespace removal between tokens</li>
 *     <li>Standardized string escaping (only quotes, backslash, control characters U+0000..U+001F)</li>
 *     <li>Number representation normalization (integers formatted without trailing decimals)</li>
 *     <li>Deterministic SHA-256 calculation for cryptographic signatures and content addressing</li>
 * </ul>
 */
public final class JsonCanonicalizer {

    private JsonCanonicalizer() {
    }

    /**
     * Canonicalizes an arbitrary JSON string according to RFC 8785.
     *
     * @param json raw JSON string
     * @return canonicalized, deterministic JSON string
     */
    public static String canonicalize(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON string cannot be null");
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be empty");
        }

        Object parsed = new FastJsonParser(trimmed).parse();
        StringBuilder sb = new StringBuilder(trimmed.length());
        serialize(parsed, sb);
        return sb.toString();
    }

    /**
     * Canonicalizes a Java Map (nested objects/arrays) according to RFC 8785.
     *
     * @param map map representing a JSON object
     * @return canonicalized JSON string
     */
    public static String canonicalize(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("Map cannot be null");
        }
        StringBuilder sb = new StringBuilder();
        serialize(map, sb);
        return sb.toString();
    }

    /**
     * Canonicalizes the JSON string and calculates its SHA-256 hash in lowercase hex format.
     *
     * @param json raw or canonical JSON string
     * @return 64-character lowercase SHA-256 hex string
     */
    public static String sha256Hex(String json) {
        String canonical = canonicalize(json);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static void serialize(Object obj, StringBuilder sb) {
        if (obj == null) {
            sb.append("null");
        } else if (obj instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (obj instanceof String s) {
            serializeString(s, sb);
        } else if (obj instanceof Number n) {
            serializeNumber(n, sb);
        } else if (obj instanceof Map<?, ?> map) {
            serializeMap(map, sb);
        } else if (obj instanceof Iterable<?> list) {
            serializeList(list, sb);
        } else if (obj instanceof Object[] arr) {
            serializeList(Arrays.asList(arr), sb);
        } else {
            // Fallback for custom objects or primitives
            serializeString(obj.toString(), sb);
        }
    }

    private static void serializeString(String s, StringBuilder sb) {
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

    private static void serializeNumber(Number n, StringBuilder sb) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (d == 0.0) {
                sb.append("0");
            } else if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(formatDouble(d));
            }
        } else if (n instanceof BigDecimal bd) {
            if (bd.compareTo(BigDecimal.ZERO) == 0) {
                sb.append("0");
            } else {
                BigDecimal stripped = bd.stripTrailingZeros();
                if (stripped.scale() <= 0) {
                    sb.append(stripped.toBigInteger().toString());
                } else {
                    sb.append(stripped.toPlainString());
                }
            }
        } else {
            sb.append(n.toString());
        }
    }

    private static String formatDouble(double d) {
        String str = Double.toString(d).toLowerCase(Locale.ROOT);
        if (str.endsWith(".0")) {
            return str.substring(0, str.length() - 2);
        }
        return str;
    }

    private static void serializeMap(Map<?, ?> map, StringBuilder sb) {
        sb.append('{');
        List<String> keys = new ArrayList<>(map.size());
        for (Object key : map.keySet()) {
            keys.add(key.toString());
        }
        // RFC 8785: UTF-16 code unit ordering
        Collections.sort(keys);

        boolean first = true;
        for (String key : keys) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            serializeString(key, sb);
            sb.append(':');
            serialize(map.get(key), sb);
        }
        sb.append('}');
    }

    private static void serializeList(Iterable<?> list, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            serialize(item, sb);
        }
        sb.append(']');
    }

    /**
     * Lightweight recursive-descent JSON parser with zero dependencies.
     */
    static final class FastJsonParser {
        private final String src;
        private int pos = 0;

        FastJsonParser(String src) {
            this.src = src;
        }

        Object parse() {
            skipWhitespace();
            Object result = parseValue();
            skipWhitespace();
            if (pos < src.length()) {
                throw new IllegalArgumentException("Unexpected trailing character at index " + pos + ": '" + src.charAt(pos) + "'");
            }
            return result;
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }

            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumber();
                    }
                    throw new IllegalArgumentException("Unexpected character at pos " + pos + ": '" + c + "'");
                }
            };
        }

        private Map<String, Object> parseObject() {
            match('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }

            while (true) {
                skipWhitespace();
                if (pos >= src.length() || src.charAt(pos) != '"') {
                    throw new IllegalArgumentException("Expected string key in object at pos " + pos);
                }
                String key = parseString();
                skipWhitespace();
                match(':');
                Object val = parseValue();
                map.put(key, val);

                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == '}') {
                    pos++;
                    break;
                }
                match(',');
            }
            return map;
        }

        private List<Object> parseArray() {
            match('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }

            while (true) {
                Object val = parseValue();
                list.add(val);

                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ']') {
                    pos++;
                    break;
                }
                match(',');
            }
            return list;
        }

        private String parseString() {
            match('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= src.length()) {
                        throw new IllegalArgumentException("Unterminated escape sequence in string");
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
                                throw new IllegalArgumentException("Incomplete unicode escape in string");
                            }
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new IllegalArgumentException("Invalid escape character: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
            boolean isFractional = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isFractional = true;
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFractional = true;
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            String numStr = src.substring(start, pos);
            if (isFractional) {
                return new BigDecimal(numStr);
            } else {
                try {
                    return Long.parseLong(numStr);
                } catch (NumberFormatException e) {
                    return new BigInteger(numStr);
                }
            }
        }

        private Object parseLiteral(String expected, Object val) {
            if (!src.startsWith(expected, pos)) {
                throw new IllegalArgumentException("Expected literal '" + expected + "' at pos " + pos);
            }
            pos += expected.length();
            return val;
        }

        private void match(char expected) {
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at pos " + pos);
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
}
