package com.msp.yaml;

import java.util.*;

/**
 * Pure Java 21 lightweight YAML serializer.
 * Produces clean, readable YAML from Java Maps, Lists, records, and primitives.
 */
public final class YamlWriter {

    private final int indentSpaces;

    public YamlWriter() {
        this(2);
    }

    public YamlWriter(int indentSpaces) {
        if (indentSpaces <= 0) {
            throw new IllegalArgumentException("Indent spaces must be greater than 0");
        }
        this.indentSpaces = indentSpaces;
    }

    /**
     * Dumps a Map to a YAML formatted string.
     *
     * @param map the map to serialize
     * @return YAML string
     */
    public static String dump(Map<String, ?> map) {
        return new YamlWriter().write(map);
    }

    /**
     * Dumps any supported object (Map, List, etc.) to a YAML formatted string.
     *
     * @param object the object to serialize
     * @return YAML string
     */
    public static String dumpObject(Object object) {
        return new YamlWriter().write(object);
    }

    /**
     * Serializes the given root object to YAML.
     *
     * @param root the object to serialize
     * @return formatted YAML
     */
    public String write(Object root) {
        StringBuilder sb = new StringBuilder();
        writeNode(root, 0, sb, false);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void writeNode(Object node, int indent, StringBuilder sb, boolean inlineFirst) {
        if (node instanceof Map<?, ?> map) {
            writeMap((Map<String, Object>) map, indent, sb, inlineFirst);
        } else if (node instanceof List<?> list) {
            writeList(list, indent, sb);
        } else {
            if (!inlineFirst) {
                sb.append(" ".repeat(indent));
            }
            sb.append(formatScalar(node)).append("\n");
        }
    }

    private void writeMap(Map<String, Object> map, int indent, StringBuilder sb, boolean inlineFirst) {
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = formatKey(entry.getKey());
            Object val = entry.getValue();

            if (first && inlineFirst) {
                sb.append(key).append(":");
            } else {
                sb.append(" ".repeat(indent)).append(key).append(":");
            }
            first = false;

            if (val instanceof Map<?, ?> subMap) {
                if (subMap.isEmpty()) {
                    sb.append(" {}\n");
                } else {
                    sb.append("\n");
                    writeMap((Map<String, Object>) subMap, indent + indentSpaces, sb, false);
                }
            } else if (val instanceof List<?> subList) {
                if (subList.isEmpty()) {
                    sb.append(" []\n");
                } else {
                    sb.append("\n");
                    writeList(subList, indent + indentSpaces, sb);
                }
            } else if (val instanceof String s && s.contains("\n")) {
                sb.append(" |\n");
                String[] lines = s.split("\r?\n", -1);
                String pad = " ".repeat(indent + indentSpaces);
                for (String line : lines) {
                    sb.append(pad).append(line).append("\n");
                }
            } else {
                sb.append(" ").append(formatScalar(val)).append("\n");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void writeList(List<?> list, int indent, StringBuilder sb) {
        String pad = " ".repeat(indent);
        for (Object item : list) {
            sb.append(pad).append("-");
            if (item instanceof Map<?, ?> mapItem) {
                if (mapItem.isEmpty()) {
                    sb.append(" {}\n");
                } else {
                    sb.append(" ");
                    // Write first entry inline, remaining entries indented
                    writeMap((Map<String, Object>) mapItem, indent + indentSpaces, sb, true);
                }
            } else if (item instanceof List<?> subList) {
                sb.append("\n");
                writeList(subList, indent + indentSpaces, sb);
            } else {
                sb.append(" ").append(formatScalar(item)).append("\n");
            }
        }
    }

    private String formatKey(String key) {
        if (key == null) {
            return "null";
        }
        if (needsQuoting(key)) {
            return quoteString(key);
        }
        return key;
    }

    private String formatScalar(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof Boolean || val instanceof Number) {
            return val.toString();
        }
        String s = val.toString();
        if (needsQuoting(s)) {
            return quoteString(s);
        }
        return s;
    }

    private boolean needsQuoting(String s) {
        if (s.isEmpty()) {
            return true;
        }
        if (s.equals("null") || s.equals("~") ||
            s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false") ||
            s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("no") ||
            s.equalsIgnoreCase("on") || s.equalsIgnoreCase("off")) {
            return true;
        }
        char first = s.charAt(0);
        if (first == '-' || first == '?' || first == ':' || first == ',' ||
            first == '[' || first == ']' || first == '{' || first == '}' ||
            first == '#' || first == '&' || first == '*' || first == '!' ||
            first == '|' || first == '>' || first == '\'' || first == '"' ||
            first == '%' || first == '@' || first == '`') {
            return true;
        }
        // Numeric strings need quoting so they aren't parsed back as numbers
        if (s.matches("^-?\\d+(\\.\\d+)?$")) {
            return true;
        }
        return s.contains(": ") || s.contains(" #") || s.contains("\n") || s.contains("\t");
    }

    private String quoteString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
