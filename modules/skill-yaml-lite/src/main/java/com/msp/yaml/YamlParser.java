package com.msp.yaml;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Pure Java 21 lightweight YAML parser.
 * Supports key-value pairs, nested maps, lists, block scalars (| and &gt;), comments,
 * and standard primitive types without any external dependencies.
 */
public final class YamlParser {

    private static final Pattern INT_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("^-?\\d+\\.\\d+([eE][+-]?\\d+)?$");

    private record LineInfo(int lineNumber, int indent, String content) {}

    private YamlParser() {}

    /**
     * Parses a YAML string into a Map of key-value pairs.
     *
     * @param yaml the YAML string to parse
     * @return Map representing the YAML structure
     * @throws YamlException if parsing fails
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String yaml) {
        Object result = parseAny(yaml);
        if (result == null) {
            return new LinkedHashMap<>();
        }
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new YamlException("Root YAML element is not a mapping: " + result.getClass().getSimpleName());
    }

    /**
     * Parses a YAML string into any supported structure (Map, List, or scalar).
     *
     * @param yaml the YAML string to parse
     * @return parsed Object (Map, List, String, Number, Boolean, or null)
     * @throws YamlException if parsing fails
     */
    public static Object parseAny(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return new LinkedHashMap<String, Object>();
        }

        List<LineInfo> lines = preprocessLines(yaml);
        if (lines.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }

        int[] pos = new int[]{0};
        return parseBlock(lines, pos, 0);
    }

    private static List<LineInfo> preprocessLines(String yaml) {
        List<LineInfo> list = new ArrayList<>();
        String[] rawLines = yaml.split("\r?\n", -1);

        for (int i = 0; i < rawLines.length; i++) {
            String raw = rawLines[i];
            int indent = countIndent(raw);
            String stripped = stripInlineComment(raw.trim());
            if (!stripped.isEmpty()) {
                list.add(new LineInfo(i + 1, indent, stripped));
            }
        }
        return list;
    }

    private static int countIndent(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                count++;
            } else if (c == '\t') {
                count += 2; // Treat tab as 2 spaces
            } else {
                break;
            }
        }
        return count;
    }

    private static String stripInlineComment(String text) {
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle && (i == 0 || text.charAt(i - 1) != '\\')) {
                inDouble = !inDouble;
            } else if (c == '#' && !inSingle && !inDouble) {
                // Must be at start of string or preceded by whitespace
                if (i == 0 || Character.isWhitespace(text.charAt(i - 1))) {
                    return text.substring(0, i).trim();
                }
            }
        }
        return text.trim();
    }

    private static Object parseBlock(List<LineInfo> lines, int[] pos, int minIndent) {
        if (pos[0] >= lines.size()) {
            return null;
        }

        LineInfo first = lines.get(pos[0]);
        if (first.indent() < minIndent) {
            return null;
        }

        if (isListItem(first.content())) {
            return parseList(lines, pos, first.indent());
        } else {
            return parseMap(lines, pos, first.indent());
        }
    }

    private static boolean isListItem(String content) {
        return content.equals("-") || content.startsWith("- ");
    }

    private static List<Object> parseList(List<LineInfo> lines, int[] pos, int listIndent) {
        List<Object> result = new ArrayList<>();

        while (pos[0] < lines.size()) {
            LineInfo line = lines.get(pos[0]);
            if (line.indent() < listIndent) {
                break;
            }
            if (line.indent() > listIndent) {
                // Unexpected nested line outside item
                break;
            }
            if (!isListItem(line.content())) {
                break;
            }

            String afterDash = line.content().equals("-") ? "" : line.content().substring(1).trim();
            pos[0]++;

            if (afterDash.isEmpty()) {
                // Value is in subsequent lines
                if (pos[0] < lines.size() && lines.get(pos[0]).indent() > listIndent) {
                    result.add(parseBlock(lines, pos, lines.get(pos[0]).indent()));
                } else {
                    result.add(null);
                }
            } else if (afterDash.equals("|") || afterDash.equals(">")) {
                result.add(parseBlockScalar(lines, pos, listIndent, afterDash.equals(">")));
            } else {
                int colonIdx = findKeyValueSeparator(afterDash);
                if (colonIdx > 0) {
                    // Item starts a map: "- key: value"
                    Map<String, Object> mapItem = new LinkedHashMap<>();
                    String key = unquote(afterDash.substring(0, colonIdx).trim());
                    String valStr = afterDash.substring(colonIdx + 1).trim();

                    if (valStr.equals("|") || valStr.equals(">")) {
                        mapItem.put(key, parseBlockScalar(lines, pos, listIndent, valStr.equals(">")));
                    } else if (valStr.isEmpty()) {
                        if (pos[0] < lines.size() && lines.get(pos[0]).indent() > listIndent) {
                            mapItem.put(key, parseBlock(lines, pos, lines.get(pos[0]).indent()));
                        } else {
                            mapItem.put(key, null);
                        }
                    } else {
                        mapItem.put(key, parseScalarOrFlow(valStr));
                    }

                    // Read subsequent map keys indented under this item (indent > listIndent)
                    int itemMapIndent = -1;
                    while (pos[0] < lines.size()) {
                        LineInfo next = lines.get(pos[0]);
                        if (next.indent() <= listIndent) {
                            break;
                        }
                        if (isListItem(next.content())) {
                            break;
                        }
                        if (itemMapIndent == -1) {
                            itemMapIndent = next.indent();
                        } else if (next.indent() < itemMapIndent) {
                            break;
                        }

                        parseMapEntry(lines, pos, itemMapIndent, mapItem);
                    }
                    result.add(mapItem);
                } else {
                    // Scalar item
                    result.add(parseScalarOrFlow(afterDash));
                }
            }
        }
        return result;
    }

    private static Map<String, Object> parseMap(List<LineInfo> lines, int[] pos, int mapIndent) {
        Map<String, Object> map = new LinkedHashMap<>();

        while (pos[0] < lines.size()) {
            LineInfo line = lines.get(pos[0]);
            if (line.indent() < mapIndent) {
                break;
            }
            if (line.indent() > mapIndent) {
                break;
            }
            if (isListItem(line.content())) {
                break;
            }

            parseMapEntry(lines, pos, mapIndent, map);
        }
        return map;
    }

    private static void parseMapEntry(List<LineInfo> lines, int[] pos, int currentIndent, Map<String, Object> targetMap) {
        LineInfo line = lines.get(pos[0]);
        int colonIdx = findKeyValueSeparator(line.content());
        if (colonIdx < 0) {
            throw new YamlException("Missing ':' delimiter", line.lineNumber());
        }

        String key = unquote(line.content().substring(0, colonIdx).trim());
        String valStr = line.content().substring(colonIdx + 1).trim();
        pos[0]++;

        if (valStr.equals("|") || valStr.equals(">")) {
            targetMap.put(key, parseBlockScalar(lines, pos, currentIndent, valStr.equals(">")));
        } else if (valStr.isEmpty()) {
            if (pos[0] < lines.size() && lines.get(pos[0]).indent() > currentIndent) {
                targetMap.put(key, parseBlock(lines, pos, lines.get(pos[0]).indent()));
            } else {
                targetMap.put(key, null);
            }
        } else {
            targetMap.put(key, parseScalarOrFlow(valStr));
        }
    }

    private static String parseBlockScalar(List<LineInfo> lines, int[] pos, int parentIndent, boolean folded) {
        List<String> blockLines = new ArrayList<>();
        Integer baseIndent = null;

        while (pos[0] < lines.size()) {
            LineInfo line = lines.get(pos[0]);
            if (line.indent() <= parentIndent) {
                break;
            }
            if (baseIndent == null) {
                baseIndent = line.indent();
            }

            int extra = Math.max(0, line.indent() - baseIndent);
            String padded = " ".repeat(extra) + line.content();
            blockLines.add(padded);
            pos[0]++;
        }

        if (blockLines.isEmpty()) {
            return "";
        }

        if (folded) {
            return String.join(" ", blockLines);
        } else {
            return String.join("\n", blockLines);
        }
    }

    private static int findKeyValueSeparator(String text) {
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle && (i == 0 || text.charAt(i - 1) != '\\')) {
                inDouble = !inDouble;
            } else if (c == ':' && !inSingle && !inDouble) {
                // Must be at end or followed by space
                if (i == text.length() - 1 || Character.isWhitespace(text.charAt(i + 1))) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static Object parseScalarOrFlow(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty() || s.equals("~") || s.equalsIgnoreCase("null")) {
            return null;
        }

        // Flow sequence: [a, b, c]
        if (s.startsWith("[") && s.endsWith("]")) {
            return parseFlowSequence(s.substring(1, s.length() - 1));
        }

        // Flow mapping: {a: 1, b: 2}
        if (s.startsWith("{") && s.endsWith("}")) {
            return parseFlowMapping(s.substring(1, s.length() - 1));
        }

        // Quoted string
        if ((s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) ||
            (s.startsWith("'") && s.endsWith("'") && s.length() >= 2)) {
            return unquote(s);
        }

        // Booleans
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("on")) {
            return Boolean.TRUE;
        }
        if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") || s.equalsIgnoreCase("off")) {
            return Boolean.FALSE;
        }

        // Numbers
        if (INT_PATTERN.matcher(s).matches()) {
            try {
                return Integer.valueOf(s);
            } catch (NumberFormatException e) {
                return Long.valueOf(s);
            }
        }
        if (DOUBLE_PATTERN.matcher(s).matches()) {
            return Double.valueOf(s);
        }

        return s;
    }

    private static List<Object> parseFlowSequence(String inner) {
        List<Object> list = new ArrayList<>();
        List<String> items = splitFlowItems(inner);
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                list.add(parseScalarOrFlow(trimmed));
            }
        }
        return list;
    }

    private static Map<String, Object> parseFlowMapping(String inner) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<String> items = splitFlowItems(inner);
        for (String item : items) {
            int colon = findKeyValueSeparator(item);
            if (colon > 0) {
                String k = unquote(item.substring(0, colon).trim());
                String v = item.substring(colon + 1).trim();
                map.put(k, parseScalarOrFlow(v));
            }
        }
        return map;
    }

    private static List<String> splitFlowItems(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                sb.append(c);
            } else if (c == '"' && !inSingle && (i == 0 || s.charAt(i - 1) != '\\')) {
                inDouble = !inDouble;
                sb.append(c);
            } else if (!inSingle && !inDouble) {
                if (c == '[' || c == '{') {
                    depth++;
                    sb.append(c);
                } else if (c == ']' || c == '}') {
                    depth--;
                    sb.append(c);
                } else if (c == ',' && depth == 0) {
                    parts.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        if (!sb.isEmpty()) {
            parts.add(sb.toString());
        }
        return parts;
    }

    private static String unquote(String s) {
        if (s == null || s.length() < 2) {
            return s;
        }
        if (s.startsWith("\"") && s.endsWith("\"")) {
            String content = s.substring(1, s.length() - 1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '\\' && i + 1 < content.length()) {
                    char next = content.charAt(i + 1);
                    switch (next) {
                        case 'n' -> { sb.append('\n'); i++; }
                        case 'r' -> { sb.append('\r'); i++; }
                        case 't' -> { sb.append('\t'); i++; }
                        case '"' -> { sb.append('"'); i++; }
                        case '\\' -> { sb.append('\\'); i++; }
                        default -> { sb.append(c); }
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        if (s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1).replace("''", "'");
        }
        return s;
    }
}
