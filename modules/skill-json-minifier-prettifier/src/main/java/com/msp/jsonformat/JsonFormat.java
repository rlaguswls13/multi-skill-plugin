package com.msp.jsonformat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Zero-dependency JSON minifier and configurable pretty-printer.
 * Thread-safe and preserves string literals and escape sequences strictly.
 */
public final class JsonFormat {

    private JsonFormat() {}

    /**
     * Pretty-prints a JSON string with 2 spaces indentation.
     */
    public static String prettyPrint(String json) {
        return prettyPrint(json, 2);
    }

    /**
     * Pretty-prints a JSON string with a specified number of spaces per indentation level.
     */
    public static String prettyPrint(String json, int indentSpaces) {
        if (indentSpaces < 0) {
            throw new IllegalArgumentException("Indent spaces must not be negative: " + indentSpaces);
        }
        return prettyPrint(json, " ".repeat(indentSpaces));
    }

    /**
     * Pretty-prints a JSON string with a custom indentation string (e.g. "\t" or "    ").
     */
    public static String prettyPrint(String json, String indentString) {
        if (json == null || json.isBlank()) {
            throw new JsonParseException("JSON input is empty or blank");
        }
        Objects.requireNonNull(indentString, "indentString must not be null");

        StringBuilder sb = new StringBuilder(json.length() * 2);
        Deque<Character> stack = new ArrayDeque<>();
        int indentLevel = 0;
        int len = json.length();
        int i = 0;

        while (i < len) {
            char c = json.charAt(i);

            // Ignore whitespace outside string literals
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // String literal
            if (c == '"') {
                sb.append('"');
                i++;
                boolean closed = false;
                while (i < len) {
                    char sc = json.charAt(i);
                    if (sc == '\\') {
                        if (i + 1 >= len) {
                            throw new JsonParseException("Unfinished escape sequence at index " + i);
                        }
                        sb.append(sc);
                        sb.append(json.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (sc == '"') {
                        sb.append('"');
                        i++;
                        closed = true;
                        break;
                    }
                    sb.append(sc);
                    i++;
                }
                if (!closed) {
                    throw new JsonParseException("Unclosed string quote in JSON");
                }
                continue;
            }

            // Object opening: {
            if (c == '{') {
                stack.push('{');
                int nextNonWs = findNextNonWhitespace(json, i + 1);
                if (nextNonWs != -1 && json.charAt(nextNonWs) == '}') {
                    stack.pop();
                    sb.append("{}");
                    i = nextNonWs + 1;
                    continue;
                }
                sb.append('{');
                indentLevel++;
                appendIndent(sb, indentLevel, indentString);
                i++;
                continue;
            }

            // Array opening: [
            if (c == '[') {
                stack.push('[');
                int nextNonWs = findNextNonWhitespace(json, i + 1);
                if (nextNonWs != -1 && json.charAt(nextNonWs) == ']') {
                    stack.pop();
                    sb.append("[]");
                    i = nextNonWs + 1;
                    continue;
                }
                sb.append('[');
                indentLevel++;
                appendIndent(sb, indentLevel, indentString);
                i++;
                continue;
            }

            // Object closing: }
            if (c == '}') {
                if (stack.isEmpty() || stack.pop() != '{') {
                    throw new JsonParseException("Unexpected closing '}' at index " + i);
                }
                indentLevel = Math.max(0, indentLevel - 1);
                appendIndent(sb, indentLevel, indentString);
                sb.append('}');
                i++;
                continue;
            }

            // Array closing: ]
            if (c == ']') {
                if (stack.isEmpty() || stack.pop() != '[') {
                    throw new JsonParseException("Unexpected closing ']' at index " + i);
                }
                indentLevel = Math.max(0, indentLevel - 1);
                appendIndent(sb, indentLevel, indentString);
                sb.append(']');
                i++;
                continue;
            }

            // Comma: ,
            if (c == ',') {
                sb.append(',');
                appendIndent(sb, indentLevel, indentString);
                i++;
                continue;
            }

            // Colon: :
            if (c == ':') {
                sb.append(": ");
                i++;
                continue;
            }

            // Other literals (numbers, true, false, null)
            sb.append(c);
            i++;
        }

        if (!stack.isEmpty()) {
            throw new JsonParseException("Unclosed bracket/brace at end of JSON: " + stack.peek());
        }

        return sb.toString();
    }

    /**
     * Minifies JSON by stripping all whitespace outside of string literals.
     */
    public static String minify(String json) {
        if (json == null || json.isBlank()) {
            throw new JsonParseException("JSON input is empty or blank");
        }

        StringBuilder sb = new StringBuilder(json.length());
        Deque<Character> stack = new ArrayDeque<>();
        int len = json.length();
        int i = 0;

        while (i < len) {
            char c = json.charAt(i);

            // String literal
            if (c == '"') {
                sb.append('"');
                i++;
                boolean closed = false;
                while (i < len) {
                    char sc = json.charAt(i);
                    if (sc == '\\') {
                        if (i + 1 >= len) {
                            throw new JsonParseException("Unfinished escape sequence at index " + i);
                        }
                        sb.append(sc);
                        sb.append(json.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (sc == '"') {
                        sb.append('"');
                        i++;
                        closed = true;
                        break;
                    }
                    sb.append(sc);
                    i++;
                }
                if (!closed) {
                    throw new JsonParseException("Unclosed string quote in JSON");
                }
                continue;
            }

            // Ignore whitespace outside string literals
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Brackets & Braces validation
            if (c == '{' || c == '[') {
                stack.push(c);
                sb.append(c);
                i++;
                continue;
            }

            if (c == '}') {
                if (stack.isEmpty() || stack.pop() != '{') {
                    throw new JsonParseException("Unexpected closing '}' at index " + i);
                }
                sb.append(c);
                i++;
                continue;
            }

            if (c == ']') {
                if (stack.isEmpty() || stack.pop() != '[') {
                    throw new JsonParseException("Unexpected closing ']' at index " + i);
                }
                sb.append(c);
                i++;
                continue;
            }

            sb.append(c);
            i++;
        }

        if (!stack.isEmpty()) {
            throw new JsonParseException("Unclosed bracket/brace at end of JSON: " + stack.peek());
        }

        return sb.toString();
    }

    /**
     * Checks whether the JSON string has balanced quotes, brackets, and braces.
     */
    public static boolean isValid(String json) {
        try {
            minify(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int findNextNonWhitespace(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            if (!Character.isWhitespace(json.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static void appendIndent(StringBuilder sb, int indentLevel, String indentString) {
        sb.append('\n');
        sb.append(indentString.repeat(Math.max(0, indentLevel)));
    }
}
