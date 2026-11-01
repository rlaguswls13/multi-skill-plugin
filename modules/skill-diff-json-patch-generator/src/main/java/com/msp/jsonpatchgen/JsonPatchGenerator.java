package com.msp.jsonpatchgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure Java 21 Diff-to-Patch engine generating RFC 6902 JSON Patch operations from two JSON strings.
 */
public final class JsonPatchGenerator {

    private JsonPatchGenerator() {
        // Utility class
    }

    /**
     * Computes the RFC 6902 JSON Patch operations needed to transform {@code sourceJson} into {@code targetJson}.
     *
     * @param sourceJson initial JSON string
     * @param targetJson target JSON string
     * @return unmodifiable list of {@link JsonPatchOperation}s
     * @throws JsonParseException if either JSON string is malformed
     */
    public static List<JsonPatchOperation> generatePatch(String sourceJson, String targetJson) {
        Object source = parseJson(sourceJson);
        Object target = parseJson(targetJson);

        List<JsonPatchOperation> patch = new ArrayList<>();
        diff(source, target, "", patch);
        return List.copyOf(patch);
    }

    /**
     * Computes the RFC 6902 JSON Patch and serializes it to an RFC 6902 JSON array string.
     *
     * @param sourceJson initial JSON string
     * @param targetJson target JSON string
     * @return RFC 6902 JSON Patch array string (e.g. {@code [{"op":"add","path":"/a","value":1}]})
     * @throws JsonParseException if either JSON string is malformed
     */
    public static String generatePatchJson(String sourceJson, String targetJson) {
        List<JsonPatchOperation> ops = generatePatch(sourceJson, targetJson);
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < ops.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            JsonPatchOperation op = ops.get(i);
            sb.append("{\"op\":\"").append(op.op().opName()).append("\",");
            sb.append("\"path\":\"").append(escapeJsonStringContent(op.path())).append('\"');
            if (op.op() != JsonPatchOp.REMOVE) {
                sb.append(",\"value\":");
                serializeValue(op.value(), sb);
            }
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void diff(Object source, Object target, String path, List<JsonPatchOperation> patch) {
        if (source instanceof Map && target instanceof Map) {
            Map<String, Object> srcMap = (Map<String, Object>) source;
            Map<String, Object> tgtMap = (Map<String, Object>) target;

            // 1. Removals
            for (String key : srcMap.keySet()) {
                if (!tgtMap.containsKey(key)) {
                    patch.add(new JsonPatchOperation(JsonPatchOp.REMOVE, childPath(path, key), null));
                }
            }

            // 2. Additions and Modifications
            for (Map.Entry<String, Object> entry : tgtMap.entrySet()) {
                String key = entry.getKey();
                Object tgtVal = entry.getValue();

                if (!srcMap.containsKey(key)) {
                    patch.add(new JsonPatchOperation(JsonPatchOp.ADD, childPath(path, key), tgtVal));
                } else {
                    Object srcVal = srcMap.get(key);
                    diff(srcVal, tgtVal, childPath(path, key), patch);
                }
            }
        } else if (source instanceof List && target instanceof List) {
            List<Object> srcList = (List<Object>) source;
            List<Object> tgtList = (List<Object>) target;

            int commonLen = Math.min(srcList.size(), tgtList.size());
            for (int i = 0; i < commonLen; i++) {
                diff(srcList.get(i), tgtList.get(i), childPath(path, String.valueOf(i)), patch);
            }

            // Additions
            for (int i = commonLen; i < tgtList.size(); i++) {
                patch.add(new JsonPatchOperation(JsonPatchOp.ADD, childPath(path, String.valueOf(i)), tgtList.get(i)));
            }

            // Removals (remove from end to avoid shifting earlier indexes)
            for (int i = srcList.size() - 1; i >= commonLen; i--) {
                patch.add(new JsonPatchOperation(JsonPatchOp.REMOVE, childPath(path, String.valueOf(i)), null));
            }
        } else {
            if (!valuesEqual(source, target)) {
                patch.add(new JsonPatchOperation(JsonPatchOp.REPLACE, path, target));
            }
        }
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (Objects.equals(a, b)) {
            return true;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue()) == 0;
        }
        return false;
    }

    private static String childPath(String basePath, String segment) {
        String escaped = segment.replace("~", "~0").replace("/", "~1");
        return basePath + "/" + escaped;
    }

    // =========================================================================
    // RFC 8259 JSON Parser (Recursive-Descent)
    // =========================================================================

    public static Object parseJson(String json) {
        if (json == null) {
            throw new JsonParseException("JSON input must not be null");
        }
        JsonParser parser = new JsonParser(json);
        Object result = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isEof()) {
            throw new JsonParseException("Unexpected trailing tokens after JSON root at position " + parser.pos);
        }
        return result;
    }

    private static final class JsonParser {
        private final String input;
        private final int length;
        private int pos = 0;

        JsonParser(String input) {
            this.input = input;
            this.length = input.length();
        }

        boolean isEof() {
            return pos >= length;
        }

        void skipWhitespace() {
            while (pos < length) {
                char c = input.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (isEof()) {
                throw new JsonParseException("Unexpected end of input while expecting JSON value");
            }
            char c = input.charAt(pos);
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
                    throw new JsonParseException("Unexpected character '" + c + "' at position " + pos);
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
                    throw new JsonParseException("Expected string key in object at position " + pos);
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object val = parseValue();
                map.put(key, val);

                skipWhitespace();
                char c = peek();
                if (c == '}') {
                    pos++;
                    break;
                } else if (c == ',') {
                    pos++;
                } else {
                    throw new JsonParseException("Expected ',' or '}' in object at position " + pos);
                }
            }
            return map;
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
                char c = peek();
                if (c == ']') {
                    pos++;
                    break;
                } else if (c == ',') {
                    pos++;
                } else {
                    throw new JsonParseException("Expected ',' or ']' in array at position " + pos);
                }
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < length) {
                char c = input.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= length) {
                        throw new JsonParseException("Unterminated escape sequence in string");
                    }
                    char esc = input.charAt(pos++);
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
                                throw new JsonParseException("Invalid unicode escape sequence in string");
                            }
                            String hex = input.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw new JsonParseException("Invalid unicode hex characters: " + hex, e);
                            }
                        }
                        default -> throw new JsonParseException("Invalid escape character: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new JsonParseException("Unterminated string literal");
        }

        private Object parseLiteral(String expected, Object value) {
            if (input.startsWith(expected, pos)) {
                pos += expected.length();
                return value;
            }
            throw new JsonParseException("Expected literal '" + expected + "' at position " + pos);
        }

        private Number parseNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (pos < length && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
            boolean isFloating = false;
            if (pos < length && input.charAt(pos) == '.') {
                isFloating = true;
                pos++;
                while (pos < length && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < length && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
                isFloating = true;
                pos++;
                if (pos < length && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < length && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            }
            String numStr = input.substring(start, pos);
            try {
                if (isFloating) {
                    return Double.parseDouble(numStr);
                } else {
                    return Long.parseLong(numStr);
                }
            } catch (NumberFormatException e) {
                throw new JsonParseException("Malformed number: " + numStr, e);
            }
        }

        private char peek() {
            if (pos >= length) {
                return '\0';
            }
            return input.charAt(pos);
        }

        private void expect(char expected) {
            if (pos >= length || input.charAt(pos) != expected) {
                throw new JsonParseException("Expected '" + expected + "' at position " + pos);
            }
            pos++;
        }
    }

    // =========================================================================
    // RFC 8259 JSON Serializer
    // =========================================================================

    @SuppressWarnings("unchecked")
    private static void serializeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof CharSequence) {
            sb.append('"').append(escapeJsonStringContent(value.toString())).append('"');
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(escapeJsonStringContent(entry.getKey().toString())).append("\":");
                serializeValue(entry.getValue(), sb);
                i++;
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                serializeValue(list.get(i), sb);
            }
            sb.append(']');
        } else {
            sb.append('"').append(escapeJsonStringContent(value.toString())).append('"');
        }
    }

    private static String escapeJsonStringContent(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
