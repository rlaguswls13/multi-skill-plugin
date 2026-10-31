package com.msp.jsondiff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Zero-dependency semantic JSON diff engine in pure Java 21.
 * Recursively inspects differences across nested objects, arrays, and primitive fields,
 * producing structured diff entries and readable change summaries.
 */
public final class SemanticJsonDiff {

    private SemanticJsonDiff() {
        // Utility class
    }

    /**
     * Computes semantic differences between two raw JSON strings.
     *
     * @param jsonBefore JSON text before changes
     * @param jsonAfter  JSON text after changes
     * @return list of semantic diff entries (empty if structurally identical)
     */
    public static List<JsonDiffEntry> diff(String jsonBefore, String jsonAfter) {
        Object beforeObj = parseJsonOrNull(jsonBefore);
        Object afterObj = parseJsonOrNull(jsonAfter);

        List<JsonDiffEntry> entries = new ArrayList<>();
        compareValues("", beforeObj, afterObj, entries);
        return Collections.unmodifiableList(entries);
    }

    /**
     * Computes semantic differences between two Maps representing JSON objects.
     *
     * @param before object map before changes
     * @param after  object map after changes
     * @return list of semantic diff entries
     */
    public static List<JsonDiffEntry> diff(Map<String, Object> before, Map<String, Object> after) {
        List<JsonDiffEntry> entries = new ArrayList<>();
        compareObjects("", before != null ? before : Map.of(), after != null ? after : Map.of(), entries);
        return Collections.unmodifiableList(entries);
    }

    /**
     * Checks whether two JSON strings have any semantic differences.
     *
     * @param before JSON text before changes
     * @param after  JSON text after changes
     * @return true if differences exist, false otherwise
     */
    public static boolean hasChanges(String before, String after) {
        return !diff(before, after).isEmpty();
    }

    /**
     * Converts a list of diff entries into a clean human-readable change summary.
     *
     * @param diffs list of diff entries
     * @return formatted multiline text summary
     */
    public static String toSummaryString(List<JsonDiffEntry> diffs) {
        if (diffs == null || diffs.isEmpty()) {
            return "(No semantic changes)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < diffs.size(); i++) {
            JsonDiffEntry d = diffs.get(i);
            if (i > 0) {
                sb.append("\n");
            }
            switch (d.type()) {
                case ADDED -> sb.append("[+] ADDED    ").append(d.path()).append(" => ").append(formatValue(d.newValue()));
                case REMOVED -> sb.append("[-] REMOVED  ").append(d.path()).append(" <= ").append(formatValue(d.oldValue()));
                case MODIFIED -> sb.append("[~] MODIFIED ").append(d.path()).append(": ")
                        .append(formatValue(d.oldValue())).append(" => ").append(formatValue(d.newValue()));
                case UNCHANGED -> sb.append("[=] UNCHANGED ").append(d.path()).append(" = ").append(formatValue(d.newValue()));
            }
        }
        return sb.toString();
    }

    // =========================================================================
    // Core Diff Logic
    // =========================================================================

    @SuppressWarnings("unchecked")
    private static void compareValues(String path, Object before, Object after, List<JsonDiffEntry> entries) {
        if (before instanceof Map<?, ?> bMap && after instanceof Map<?, ?> aMap) {
            compareObjects(path, (Map<String, Object>) bMap, (Map<String, Object>) aMap, entries);
        } else if (before instanceof List<?> bList && after instanceof List<?> aList) {
            compareLists(path, (List<Object>) bList, (List<Object>) aList, entries);
        } else if (!areSemanticallyEqual(before, after)) {
            if (before == null && after != null) {
                entries.add(new JsonDiffEntry(path.isEmpty() ? "$" : path, DiffType.ADDED, null, after));
            } else if (before != null && after == null) {
                entries.add(new JsonDiffEntry(path.isEmpty() ? "$" : path, DiffType.REMOVED, before, null));
            } else {
                entries.add(new JsonDiffEntry(path.isEmpty() ? "$" : path, DiffType.MODIFIED, before, after));
            }
        }
    }

    private static void compareObjects(String currentPath, Map<String, Object> before, Map<String, Object> after, List<JsonDiffEntry> entries) {
        Set<String> allKeys = new TreeSet<>();
        if (before != null) {
            allKeys.addAll(before.keySet());
        }
        if (after != null) {
            allKeys.addAll(after.keySet());
        }

        for (String key : allKeys) {
            String path = currentPath.isEmpty() ? key : currentPath + "." + key;
            boolean inBefore = before != null && before.containsKey(key);
            boolean inAfter = after != null && after.containsKey(key);

            if (inBefore && !inAfter) {
                entries.add(new JsonDiffEntry(path, DiffType.REMOVED, before.get(key), null));
            } else if (!inBefore && inAfter) {
                entries.add(new JsonDiffEntry(path, DiffType.ADDED, null, after.get(key)));
            } else {
                Object bVal = before.get(key);
                Object aVal = after.get(key);
                compareValues(path, bVal, aVal, entries);
            }
        }
    }

    private static void compareLists(String currentPath, List<Object> before, List<Object> after, List<JsonDiffEntry> entries) {
        int bLen = before != null ? before.size() : 0;
        int aLen = after != null ? after.size() : 0;
        int maxLen = Math.max(bLen, aLen);

        for (int i = 0; i < maxLen; i++) {
            String path = currentPath + "[" + i + "]";
            if (i < bLen && i < aLen) {
                compareValues(path, before.get(i), after.get(i), entries);
            } else if (i < bLen) {
                entries.add(new JsonDiffEntry(path, DiffType.REMOVED, before.get(i), null));
            } else {
                entries.add(new JsonDiffEntry(path, DiffType.ADDED, null, after.get(i)));
            }
        }
    }

    private static boolean areSemanticallyEqual(Object o1, Object o2) {
        if (Objects.equals(o1, o2)) {
            return true;
        }
        if (o1 instanceof Number n1 && o2 instanceof Number n2) {
            return Double.compare(n1.doubleValue(), n2.doubleValue()) == 0;
        }
        return false;
    }

    private static String formatValue(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof String s) {
            return "\"" + s + "\"";
        }
        return String.valueOf(val);
    }

    private static Object parseJsonOrNull(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return new MiniJsonParser(json).parse();
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // Embedded Zero-Dependency JSON Parser
    // =========================================================================

    private static final class MiniJsonParser {
        private final String src;
        private final int len;
        private int pos = 0;

        MiniJsonParser(String src) {
            this.src = src;
            this.len = src.length();
        }

        Object parse() {
            skipWhitespace();
            Object val = parseValue();
            skipWhitespace();
            return val;
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= len) {
                return null;
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
                parseNull();
                return null;
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber();
            }
            throw new IllegalArgumentException("Unexpected character at " + pos + ": " + c);
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (pos < len && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < len) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (pos < len && src.charAt(pos) == ',') {
                    pos++;
                } else if (pos < len && src.charAt(pos) == '}') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at " + pos);
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (pos < len && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < len) {
                skipWhitespace();
                Object elem = parseValue();
                list.add(elem);
                skipWhitespace();
                if (pos < len && src.charAt(pos) == ',') {
                    pos++;
                } else if (pos < len && src.charAt(pos) == ']') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at " + pos);
                }
            }
            return list;
        }

        private String parseString() {
            expect('"');
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
            while (pos < len) {
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
            while (pos < len) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private void expect(char expected) {
            if (pos >= len || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at " + pos);
            }
            pos++;
        }
    }
}
