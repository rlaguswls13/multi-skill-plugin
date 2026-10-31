package com.msp.jsonmasker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Selective JSON field masker supporting JSONPath patterns, wildcards, and custom masking strategies.
 * Strictly zero 3rd-party runtime dependencies (Pure Java 21).
 */
public final class JsonMasker {

    private final List<MaskRule> rules = new ArrayList<>();

    private record MaskRule(List<String> patternSegments, MaskingStrategy strategy) {}

    private JsonMasker() {
        // Private constructor, use create()
    }

    /**
     * Factory method creating a new JsonMasker instance.
     */
    public static JsonMasker create() {
        return new JsonMasker();
    }

    /**
     * Registers a masking strategy for a specific path pattern.
     * Supports exact paths ("password", "user.password") and wildcards ("*.ssn", "users[*].email").
     *
     * @param pathPattern JSONPath or dot-separated path pattern
     * @param strategy    masking strategy to apply
     * @return this JsonMasker instance for fluent chaining
     */
    public JsonMasker withMask(String pathPattern, MaskingStrategy strategy) {
        Objects.requireNonNull(pathPattern, "pathPattern must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        List<String> segments = parsePattern(pathPattern);
        if (!segments.isEmpty()) {
            rules.add(new MaskRule(segments, strategy));
        }
        return this;
    }

    /**
     * Masks targeted fields in the given JSON string.
     * If the input is null, empty, or invalid JSON, it is safely returned without modification (pass-through).
     *
     * @param json input JSON string
     * @return JSON string with targeted fields masked
     */
    public String mask(String json) {
        if (json == null || json.isBlank() || rules.isEmpty()) {
            return json;
        }

        try {
            JsonParser parser = new JsonParser(json);
            JsonValue root = parser.parse();
            JsonValue masked = maskValue(root, List.of());
            return masked.toJson();
        } catch (Exception e) {
            // Invalid JSON pass-through handling
            return json;
        }
    }

    private JsonValue maskValue(JsonValue node, List<String> currentPath) {
        if (node instanceof JsonObject jo) {
            Map<String, JsonValue> newMap = new LinkedHashMap<>();
            for (Map.Entry<String, JsonValue> entry : jo.members.entrySet()) {
                String key = entry.getKey();
                List<String> childPath = new ArrayList<>(currentPath);
                childPath.add(key);

                MaskingStrategy strat = findStrategy(childPath);
                JsonValue childVal = entry.getValue();
                if (strat != null) {
                    newMap.put(key, maskLeaf(childVal, strat));
                } else {
                    newMap.put(key, maskValue(childVal, childPath));
                }
            }
            return new JsonObject(newMap);
        } else if (node instanceof JsonArray ja) {
            List<JsonValue> newList = new ArrayList<>(ja.elements.size());
            for (int i = 0; i < ja.elements.size(); i++) {
                List<String> childPath = new ArrayList<>(currentPath);
                childPath.add(String.valueOf(i));

                MaskingStrategy strat = findStrategy(childPath);
                JsonValue elem = ja.elements.get(i);
                if (strat != null) {
                    newList.add(maskLeaf(elem, strat));
                } else {
                    newList.add(maskValue(elem, childPath));
                }
            }
            return new JsonArray(newList);
        }
        return node;
    }

    private JsonValue maskLeaf(JsonValue val, MaskingStrategy strategy) {
        if (val instanceof JsonString js) {
            return new JsonString(strategy.mask(js.value));
        } else if (val instanceof JsonNumber jn) {
            return new JsonString(strategy.mask(jn.raw));
        } else if (val instanceof JsonBoolean jb) {
            return new JsonString(strategy.mask(String.valueOf(jb.value)));
        } else if (val instanceof JsonNull) {
            return new JsonString(strategy.mask("null"));
        } else {
            return new JsonString(strategy.mask(val.toJson()));
        }
    }

    private MaskingStrategy findStrategy(List<String> currentPath) {
        for (MaskRule rule : rules) {
            if (matches(currentPath, rule.patternSegments)) {
                return rule.strategy;
            }
        }
        return null;
    }

    private boolean matches(List<String> currentPath, List<String> patternSegments) {
        if (currentPath.isEmpty() || patternSegments.isEmpty()) {
            return false;
        }

        // Exact match by segment length with wildcard support
        if (currentPath.size() == patternSegments.size()) {
            boolean allMatch = true;
            for (int i = 0; i < currentPath.size(); i++) {
                String p = patternSegments.get(i);
                String c = currentPath.get(i);
                if (!p.equals("*") && !p.equalsIgnoreCase(c)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return true;
            }
        }

        // Leaf field match for simple 1-segment patterns (e.g. "password" matching "user.password")
        if (patternSegments.size() == 1 && !patternSegments.get(0).equals("*")) {
            String leaf = currentPath.get(currentPath.size() - 1);
            if (patternSegments.get(0).equalsIgnoreCase(leaf)) {
                return true;
            }
        }

        return false;
    }

    private static List<String> parsePattern(String pattern) {
        String p = pattern.trim();
        if (p.startsWith("$.")) {
            p = p.substring(2);
        } else if (p.startsWith("$")) {
            p = p.substring(1);
        }
        p = p.replaceAll("\\[([^\\]]+)\\]", ".$1");
        if (p.startsWith(".")) {
            p = p.substring(1);
        }
        String[] parts = p.split("\\.");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                list.add(part);
            }
        }
        return list;
    }

    // --- Lightweight Internal JSON Parser & AST ---

    private sealed interface JsonValue permits JsonObject, JsonArray, JsonString, JsonNumber, JsonBoolean, JsonNull {
        String toJson();
    }

    private record JsonObject(Map<String, JsonValue> members) implements JsonValue {
        @Override
        public String toJson() {
            return "{" + members.entrySet().stream()
                    .map(e -> "\"" + escape(e.getKey()) + "\":" + e.getValue().toJson())
                    .collect(Collectors.joining(",")) + "}";
        }
    }

    private record JsonArray(List<JsonValue> elements) implements JsonValue {
        @Override
        public String toJson() {
            return "[" + elements.stream()
                    .map(JsonValue::toJson)
                    .collect(Collectors.joining(",")) + "]";
        }
    }

    private record JsonString(String value) implements JsonValue {
        @Override
        public String toJson() {
            return "\"" + escape(value) + "\"";
        }
    }

    private record JsonNumber(String raw) implements JsonValue {
        @Override
        public String toJson() {
            return raw;
        }
    }

    private record JsonBoolean(boolean value) implements JsonValue {
        @Override
        public String toJson() {
            return value ? "true" : "false";
        }
    }

    private record JsonNull() implements JsonValue {
        @Override
        public String toJson() {
            return "null";
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
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

    private static final class JsonParser {
        private final String src;
        private int pos = 0;

        JsonParser(String src) {
            this.src = src;
        }

        JsonValue parse() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Empty JSON string");
            }
            JsonValue val = parseValue();
            skipWhitespace();
            if (pos < src.length()) {
                throw new IllegalArgumentException("Trailing characters at position " + pos);
            }
            return val;
        }

        private JsonValue parseValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return new JsonString(parseString());
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + pos);
        }

        private JsonObject parseObject() {
            expect('{');
            skipWhitespace();
            Map<String, JsonValue> map = new LinkedHashMap<>();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return new JsonObject(map);
            }
            while (pos < src.length()) {
                skipWhitespace();
                if (pos >= src.length() || src.charAt(pos) != '"') {
                    throw new IllegalArgumentException("Expected string key at position " + pos);
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                JsonValue val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                    continue;
                }
                if (pos < src.length() && src.charAt(pos) == '}') {
                    pos++;
                    return new JsonObject(map);
                }
                throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
            }
            throw new IllegalArgumentException("Unclosed object");
        }

        private JsonArray parseArray() {
            expect('[');
            skipWhitespace();
            List<JsonValue> list = new ArrayList<>();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return new JsonArray(list);
            }
            while (pos < src.length()) {
                JsonValue val = parseValue();
                list.add(val);
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                    continue;
                }
                if (pos < src.length() && src.charAt(pos) == ']') {
                    pos++;
                    return new JsonArray(list);
                }
                throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
            }
            throw new IllegalArgumentException("Unclosed array");
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= src.length()) throw new IllegalArgumentException("Unterminated escape");
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
                            if (pos + 4 > src.length()) throw new IllegalArgumentException("Invalid unicode escape");
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
            throw new IllegalArgumentException("Unclosed string literal");
        }

        private JsonNumber parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
            if (pos < src.length() && src.charAt(pos) == '.') {
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            return new JsonNumber(src.substring(start, pos));
        }

        private JsonBoolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return new JsonBoolean(true);
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return new JsonBoolean(false);
            }
            throw new IllegalArgumentException("Invalid boolean literal at position " + pos);
        }

        private JsonNull parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return new JsonNull();
            }
            throw new IllegalArgumentException("Invalid null literal at position " + pos);
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

        private void expect(char c) {
            if (pos >= src.length() || src.charAt(pos) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }
    }
}
